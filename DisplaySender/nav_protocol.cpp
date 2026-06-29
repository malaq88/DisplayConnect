#include "nav_protocol.h"
#include <ArduinoJson.h>
#include <string.h>

static void copyTruncated(char* dest, size_t destSize, const char* src) {
  if (dest == nullptr || destSize == 0) {
    return;
  }
  if (src == nullptr) {
    dest[0] = '\0';
    return;
  }
  strncpy(dest, src, destSize - 1);
  dest[destSize - 1] = '\0';
}

bool parseNavJson(const char* json, size_t length, NavState& state) {
  if (json == nullptr || length == 0) {
    return false;
  }

  JsonDocument doc;
  const DeserializationError error = deserializeJson(doc, json, length);
  if (error) {
    Serial.printf("JSON error: %s\n", error.c_str());
    return false;
  }

  const char* type = doc["type"] | "";
  if (strcmp(type, "heartbeat") == 0) {
    return false;
  }
  if (strcmp(type, "nav") != 0) {
    return false;
  }

  state.valid = true;
  state.lat = doc["lat"] | 0.0;
  state.lon = doc["lon"] | 0.0;
  state.bearing = doc["bearing"] | 0.0f;
  state.distanceM = doc["distance_m"] | 0;
  copyTruncated(state.instruction, sizeof(state.instruction), doc["instruction"] | "");
  copyTruncated(state.street, sizeof(state.street), doc["street"] | "");

  const char* htmlField = doc["html"] | "";
  if (htmlField[0] != '\0') {
    copyTruncated(state.html, sizeof(state.html), htmlField);
    state.hasHtml = true;
  } else {
    state.html[0] = '\0';
    state.hasHtml = false;
  }

  state.routeCount = 0;
  state.streetSegmentCount = 0;
  state.hasUserPosition = false;

  JsonArray route = doc["route"].as<JsonArray>();
  if (!route.isNull()) {
    for (JsonArray point : route) {
      if (state.routeCount >= NAV_MAX_ROUTE_POINTS) {
        break;
      }
      if (point.size() < 2) {
        continue;
      }
      state.routeX[state.routeCount] = point[0].as<int16_t>();
      state.routeY[state.routeCount] = point[1].as<int16_t>();
      state.routeCount++;
    }
  }

  JsonArray streets = doc["streets"].as<JsonArray>();
  if (!streets.isNull()) {
    for (JsonArray segment : streets) {
      if (state.streetSegmentCount >= NAV_MAX_STREET_SEGMENTS) {
        break;
      }
      if (segment.size() < 4) {
        continue;
      }
      state.streetX0[state.streetSegmentCount] = segment[0].as<int16_t>();
      state.streetY0[state.streetSegmentCount] = segment[1].as<int16_t>();
      state.streetX1[state.streetSegmentCount] = segment[2].as<int16_t>();
      state.streetY1[state.streetSegmentCount] = segment[3].as<int16_t>();
      state.streetSegmentCount++;
    }
  }

  if (doc["user_x"].is<int>() && doc["user_y"].is<int>()) {
    state.userX = doc["user_x"].as<int16_t>();
    state.userY = doc["user_y"].as<int16_t>();
    state.hasUserPosition = true;
  } else if (state.routeCount > 0) {
    state.userX = state.routeX[state.routeCount - 1];
    state.userY = state.routeY[state.routeCount - 1];
    state.hasUserPosition = state.userX >= 0 && state.userY >= 0;
  }

  return true;
}
