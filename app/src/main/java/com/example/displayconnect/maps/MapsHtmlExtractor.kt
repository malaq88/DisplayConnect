package com.example.displayconnect.maps

object MapsHtmlExtractor {

    /**
     * JavaScript run inside Google Maps WebView to build a small HTML snippet
     * from visible navigation text (best-effort; DOM may change).
     */
    val EXTRACT_SCRIPT = """
        (function() {
          try {
            var lines = [];
            var seen = {};
            var nodes = document.querySelectorAll(
              '[role="button"], [role="heading"], h1, h2, h3, .section-direction-trip, [data-trip-index]'
            );
            if (nodes.length === 0) {
              nodes = document.querySelectorAll('div, span');
            }
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              if (!el || !el.innerText) continue;
              var t = el.innerText.trim().replace(/\s+/g, ' ');
              if (t.length < 2 || t.length > 100) continue;
              if (seen[t]) continue;
              if (!/(\d+\s*(m|km|min|h))|(\bturn\b|\bdir\b|\besq\b|\bdir\b|→|←)/i.test(t)) continue;
              seen[t] = true;
              lines.push(t);
              if (lines.length >= 4) break;
            }
            if (lines.length === 0) {
              var body = (document.body && document.body.innerText) ? document.body.innerText : '';
              body = body.split('\n').map(function(s){ return s.trim(); }).filter(function(s){
                return s.length > 2 && s.length < 80;
              }).slice(0, 3);
              lines = body;
            }
            if (lines.length === 0) return '';
            return '<div>' + lines.join('</div><div>') + '</div>';
          } catch (e) {
            return '';
          }
        })();
    """.trimIndent()

    fun buildMapsDirectionsUrl(destLat: Double, destLon: Double): String {
        return "https://www.google.com/maps/dir/?api=1" +
            "&destination=$destLat,$destLon" +
            "&travelmode=bicycling"
    }
}
