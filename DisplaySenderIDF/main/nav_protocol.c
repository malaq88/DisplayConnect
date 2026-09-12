#include "nav_protocol.h"

#include <string.h>
#include <stdlib.h>

#include "cJSON.h"
#include "esp_log.h"

static const char *TAG = "nav_proto";

static void copy_truncated(char *dest, size_t dest_size, const char *src)
{
    if (!dest || dest_size == 0) {
        return;
    }
    if (!src) {
        dest[0] = '\0';
        return;
    }
    strncpy(dest, src, dest_size - 1);
    dest[dest_size - 1] = '\0';
}

bool is_loading_json(const char *json, size_t length)
{
    if (!json || length < 10) {
        return false;
    }
    return strstr(json, "\"type\":\"loading\"") != NULL ||
           strstr(json, "\"type\": \"loading\"") != NULL;
}

bool parse_nav_json(const char *json, size_t length, nav_state_t *state)
{
    if (!json || length == 0 || !state) {
        return false;
    }

    cJSON *doc = cJSON_ParseWithLength(json, length);
    if (!doc) {
        ESP_LOGW(TAG, "JSON parse error");
        return false;
    }

    const cJSON *type = cJSON_GetObjectItemCaseSensitive(doc, "type");
    if (!cJSON_IsString(type) || !type->valuestring) {
        cJSON_Delete(doc);
        return false;
    }
    if (strcmp(type->valuestring, "heartbeat") == 0 ||
        strcmp(type->valuestring, "loading") == 0 ||
        strcmp(type->valuestring, "nav") != 0) {
        cJSON_Delete(doc);
        return false;
    }

    memset(state, 0, sizeof(*state));
    state->valid = true;
    state->remaining_m = -1;
    state->remaining_s = -1;

    const cJSON *lat = cJSON_GetObjectItemCaseSensitive(doc, "lat");
    const cJSON *lon = cJSON_GetObjectItemCaseSensitive(doc, "lon");
    const cJSON *bearing = cJSON_GetObjectItemCaseSensitive(doc, "bearing");
    const cJSON *distance = cJSON_GetObjectItemCaseSensitive(doc, "distance_m");
    const cJSON *remaining_m = cJSON_GetObjectItemCaseSensitive(doc, "remaining_m");
    const cJSON *remaining_s = cJSON_GetObjectItemCaseSensitive(doc, "remaining_s");
    const cJSON *off_route = cJSON_GetObjectItemCaseSensitive(doc, "off_route");
    const cJSON *lang = cJSON_GetObjectItemCaseSensitive(doc, "lang");
    const cJSON *gps_weak = cJSON_GetObjectItemCaseSensitive(doc, "gps_weak");
    if (cJSON_IsNumber(lat)) {
        state->lat = lat->valuedouble;
    }
    if (cJSON_IsNumber(lon)) {
        state->lon = lon->valuedouble;
    }
    if (cJSON_IsNumber(bearing)) {
        state->bearing = (float)bearing->valuedouble;
    }
    if (cJSON_IsNumber(distance)) {
        state->distance_m = distance->valueint;
    }
    if (cJSON_IsNumber(remaining_m)) {
        state->remaining_m = remaining_m->valueint;
    }
    if (cJSON_IsNumber(remaining_s)) {
        state->remaining_s = remaining_s->valueint;
    }
    if (cJSON_IsBool(off_route)) {
        state->off_route = cJSON_IsTrue(off_route);
    }
    if (cJSON_IsString(lang) && lang->valuestring) {
        state->english = strcmp(lang->valuestring, "en") == 0;
    }
    if (cJSON_IsBool(gps_weak)) {
        state->gps_weak = cJSON_IsTrue(gps_weak);
    }

    const cJSON *instruction = cJSON_GetObjectItemCaseSensitive(doc, "instruction");
    const cJSON *street = cJSON_GetObjectItemCaseSensitive(doc, "street");
    const cJSON *html = cJSON_GetObjectItemCaseSensitive(doc, "html");
    copy_truncated(state->instruction, sizeof(state->instruction),
                   cJSON_IsString(instruction) ? instruction->valuestring : "");
    copy_truncated(state->street, sizeof(state->street),
                   cJSON_IsString(street) ? street->valuestring : "");
    if (cJSON_IsString(html) && html->valuestring && html->valuestring[0] != '\0') {
        copy_truncated(state->html, sizeof(state->html), html->valuestring);
        state->has_html = true;
    }

    const cJSON *route = cJSON_GetObjectItemCaseSensitive(doc, "route");
    if (cJSON_IsArray(route)) {
        const cJSON *point = NULL;
        cJSON_ArrayForEach(point, route) {
            if (state->route_count >= NAV_MAX_ROUTE_POINTS) {
                break;
            }
            if (!cJSON_IsArray(point) || cJSON_GetArraySize(point) < 2) {
                continue;
            }
            state->route_x[state->route_count] = (int16_t)cJSON_GetArrayItem(point, 0)->valueint;
            state->route_y[state->route_count] = (int16_t)cJSON_GetArrayItem(point, 1)->valueint;
            state->route_count++;
        }
    }

    const cJSON *streets = cJSON_GetObjectItemCaseSensitive(doc, "streets");
    if (cJSON_IsArray(streets)) {
        const cJSON *seg = NULL;
        cJSON_ArrayForEach(seg, streets) {
            if (state->street_segment_count >= NAV_MAX_STREET_SEGMENTS) {
                break;
            }
            if (!cJSON_IsArray(seg) || cJSON_GetArraySize(seg) < 4) {
                continue;
            }
            int i = state->street_segment_count;
            state->street_x0[i] = (int16_t)cJSON_GetArrayItem(seg, 0)->valueint;
            state->street_y0[i] = (int16_t)cJSON_GetArrayItem(seg, 1)->valueint;
            state->street_x1[i] = (int16_t)cJSON_GetArrayItem(seg, 2)->valueint;
            state->street_y1[i] = (int16_t)cJSON_GetArrayItem(seg, 3)->valueint;
            state->street_segment_count++;
        }
    }

    const cJSON *ux = cJSON_GetObjectItemCaseSensitive(doc, "user_x");
    const cJSON *uy = cJSON_GetObjectItemCaseSensitive(doc, "user_y");
    if (cJSON_IsNumber(ux) && cJSON_IsNumber(uy)) {
        state->user_x = (int16_t)ux->valueint;
        state->user_y = (int16_t)uy->valueint;
        state->has_user_position = true;
    } else if (state->route_count > 0) {
        state->user_x = state->route_x[state->route_count - 1];
        state->user_y = state->route_y[state->route_count - 1];
        state->has_user_position = state->user_x >= 0 && state->user_y >= 0;
    }

    cJSON_Delete(doc);
    return true;
}
