#include "ble_nus.h"
#include "config.h"

#include <string.h>

#include "esp_bt.h"
#include "esp_log.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_store.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

static const char *TAG = "ble_nus";

/* Nordic UART UUIDs (little-endian byte order for BLE_UUID128_INIT) */
static const ble_uuid128_t uuid_nus_svc =
    BLE_UUID128_INIT(0x9e, 0xca, 0xdc, 0x24, 0x0e, 0xe5, 0xa9, 0xe0,
                     0x93, 0xf3, 0xa3, 0xb5, 0x01, 0x00, 0x40, 0x6e);
static const ble_uuid128_t uuid_nus_rx =
    BLE_UUID128_INIT(0x9e, 0xca, 0xdc, 0x24, 0x0e, 0xe5, 0xa9, 0xe0,
                     0x93, 0xf3, 0xa3, 0xb5, 0x02, 0x00, 0x40, 0x6e);
static const ble_uuid128_t uuid_nus_tx =
    BLE_UUID128_INIT(0x9e, 0xca, 0xdc, 0x24, 0x0e, 0xe5, 0xa9, 0xe0,
                     0x93, 0xf3, 0xa3, 0xb5, 0x03, 0x00, 0x40, 0x6e);

static uint16_t s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
static uint16_t s_tx_val_handle;
static uint8_t s_own_addr_type;
static ble_nus_callbacks_t s_cbs;
static bool s_started;

static int gap_event(struct ble_gap_event *event, void *arg);
static int gatt_svr_chr_access(uint16_t conn_handle, uint16_t attr_handle,
                               struct ble_gatt_access_ctxt *ctxt, void *arg);

static const struct ble_gatt_svc_def gatt_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &uuid_nus_svc.u,
        .characteristics = (struct ble_gatt_chr_def[]){
            {
                .uuid = &uuid_nus_rx.u,
                .access_cb = gatt_svr_chr_access,
                .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP,
            },
            {
                .uuid = &uuid_nus_tx.u,
                .access_cb = gatt_svr_chr_access,
                .val_handle = &s_tx_val_handle,
                .flags = BLE_GATT_CHR_F_NOTIFY | BLE_GATT_CHR_F_READ,
            },
            {0},
        },
    },
    {0},
};

static int gatt_svr_chr_access(uint16_t conn_handle, uint16_t attr_handle,
                               struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)conn_handle;
    (void)attr_handle;
    (void)arg;

    if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR) {
        uint16_t len = OS_MBUF_PKTLEN(ctxt->om);
        if (len == 0 || !s_cbs.on_rx) {
            return 0;
        }
        uint8_t tmp[128];
        uint16_t offset = 0;
        while (offset < len) {
            uint16_t chunk = (uint16_t)(len - offset);
            if (chunk > sizeof(tmp)) {
                chunk = sizeof(tmp);
            }
            int rc = os_mbuf_copydata(ctxt->om, offset, chunk, tmp);
            if (rc != 0) {
                return BLE_ATT_ERR_UNLIKELY;
            }
            s_cbs.on_rx(tmp, chunk, s_cbs.ctx);
            offset = (uint16_t)(offset + chunk);
        }
        return 0;
    }

    if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
        return os_mbuf_append(ctxt->om, "", 0) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }

    return BLE_ATT_ERR_UNLIKELY;
}

static void start_advertising(void)
{
    /*
     * BLE ADV PDU is only 31 bytes. Complete name ("DisplayConnect-S3" = 16)
     * + 128-bit UUID (18) + flags will NOT fit → ble_gap_adv_set_fields fails
     * and the phone never sees the device.
     *
     * Android BleNavClient filters by NUS service UUID, so UUID must be in the
     * primary advertising packet. Put the name in the scan response.
     */
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = (ble_uuid128_t *)&uuid_nus_svc;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    fields.tx_pwr_lvl_is_present = 1;
    fields.tx_pwr_lvl = 0; /* filled by stack when present */

    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "adv set fields rc=%d — retry without TX power", rc);
        fields.tx_pwr_lvl_is_present = 0;
        rc = ble_gap_adv_set_fields(&fields);
        if (rc != 0) {
            ESP_LOGE(TAG, "adv set fields failed rc=%d", rc);
            return;
        }
    }

    struct ble_hs_adv_fields rsp = {0};
    rsp.name = (uint8_t *)BLE_DEVICE_NAME;
    rsp.name_len = (uint8_t)strlen(BLE_DEVICE_NAME);
    rsp.name_is_complete = 1;
    rc = ble_gap_adv_rsp_set_fields(&rsp);
    if (rc != 0) {
        ESP_LOGW(TAG, "adv rsp (name) rc=%d — advertising UUID-only", rc);
    }

    struct ble_gap_adv_params adv = {0};
    adv.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv.disc_mode = BLE_GAP_DISC_MODE_GEN;
    adv.itvl_min = BLE_GAP_ADV_FAST_INTERVAL1_MIN;
    adv.itvl_max = BLE_GAP_ADV_FAST_INTERVAL1_MAX;

    rc = ble_gap_adv_start(s_own_addr_type, NULL, BLE_HS_FOREVER, &adv, gap_event, NULL);
    if (rc != 0) {
        ESP_LOGE(TAG, "adv start rc=%d", rc);
        return;
    }
    ESP_LOGI(TAG, "Advertising NUS UUID + name \"%s\" (scan rsp)", BLE_DEVICE_NAME);
}

static int gap_event(struct ble_gap_event *event, void *arg)
{
    (void)arg;
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0) {
            s_conn_handle = event->connect.conn_handle;
            ESP_LOGI(TAG, "Connected handle=%u", s_conn_handle);
            ble_att_set_preferred_mtu(BLE_PREFERRED_MTU);
            if (s_cbs.on_conn) {
                s_cbs.on_conn(true, s_cbs.ctx);
            }
        } else {
            ESP_LOGW(TAG, "Connect failed status=%d — readvertise", event->connect.status);
            start_advertising();
        }
        return 0;

    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI(TAG, "Disconnected reason=%d", event->disconnect.reason);
        s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
        if (s_cbs.on_conn) {
            s_cbs.on_conn(false, s_cbs.ctx);
        }
        return 0;

    case BLE_GAP_EVENT_MTU:
        ESP_LOGI(TAG, "MTU updated to %u", event->mtu.value);
        return 0;

    case BLE_GAP_EVENT_SUBSCRIBE:
        ESP_LOGI(TAG, "Subscribe attr=%u notify=%d",
                 event->subscribe.attr_handle, event->subscribe.cur_notify);
        return 0;

    case BLE_GAP_EVENT_ADV_COMPLETE:
        ESP_LOGW(TAG, "ADV complete reason=%d — restart", event->adv_complete.reason);
        start_advertising();
        return 0;

    default:
        return 0;
    }
}

static void on_sync(void)
{
    /* Max advertise / default TX power so phones nearby see the board. */
    esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_DEFAULT, ESP_PWR_LVL_P9);
    esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, ESP_PWR_LVL_P9);

    int rc = ble_hs_util_ensure_addr(0);
    if (rc != 0) {
        ESP_LOGE(TAG, "ensure addr rc=%d", rc);
        return;
    }
    rc = ble_hs_id_infer_auto(0, &s_own_addr_type);
    if (rc != 0) {
        ESP_LOGE(TAG, "infer addr rc=%d", rc);
        return;
    }

    uint8_t addr_val[6] = {0};
    ble_hs_id_copy_addr(s_own_addr_type, addr_val, NULL);
    ESP_LOGI(TAG, "Device addr %02x:%02x:%02x:%02x:%02x:%02x type=%u",
             addr_val[5], addr_val[4], addr_val[3], addr_val[2], addr_val[1], addr_val[0],
             s_own_addr_type);

    start_advertising();
}

static void on_reset(int reason)
{
    ESP_LOGE(TAG, "NimBLE reset reason=%d", reason);
}

static void ble_host_task(void *param)
{
    (void)param;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

esp_err_t ble_nus_start(const ble_nus_callbacks_t *cbs)
{
    if (!cbs) {
        return ESP_ERR_INVALID_ARG;
    }
    s_cbs = *cbs;

    ESP_ERROR_CHECK(nimble_port_init());
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.reset_cb = on_reset;
    ble_hs_cfg.gatts_register_cb = NULL;
    ble_hs_cfg.store_status_cb = ble_store_util_status_rr;

    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_gatts_count_cfg(gatt_svcs);
    if (rc != 0) {
        ESP_LOGE(TAG, "gatts_count_cfg rc=%d", rc);
        return ESP_FAIL;
    }
    rc = ble_gatts_add_svcs(gatt_svcs);
    if (rc != 0) {
        ESP_LOGE(TAG, "gatts_add_svcs rc=%d", rc);
        return ESP_FAIL;
    }
    rc = ble_svc_gap_device_name_set(BLE_DEVICE_NAME);
    if (rc != 0) {
        ESP_LOGE(TAG, "device_name_set rc=%d", rc);
        return ESP_FAIL;
    }

    nimble_port_freertos_init(ble_host_task);
    s_started = true;
    ESP_LOGI(TAG, "NUS server started");
    return ESP_OK;
}

esp_err_t ble_nus_notify(const char *text)
{
    if (!text || s_conn_handle == BLE_HS_CONN_HANDLE_NONE) {
        return ESP_ERR_INVALID_STATE;
    }
    struct os_mbuf *om = ble_hs_mbuf_from_flat(text, strlen(text));
    if (!om) {
        return ESP_ERR_NO_MEM;
    }
    int rc = ble_gatts_notify_custom(s_conn_handle, s_tx_val_handle, om);
    return rc == 0 ? ESP_OK : ESP_FAIL;
}

void ble_nus_restart_advertising(void)
{
    if (!s_started) {
        return;
    }
    if (ble_gap_adv_active()) {
        return;
    }
    start_advertising();
}

bool ble_nus_is_connected(void)
{
    return s_conn_handle != BLE_HS_CONN_HANDLE_NONE;
}
