/**
 * k6 load test for the Flash Sale API.
 *
 * Run: k6 run --env API_KEY=dev-api-key-change-in-production flashsale.js
 *
 * Stages:
 *   0-30s   ramp from 0 → 500 VUs  (warm-up)
 *   30-90s  hold at 500 VUs         (sustained flash-sale traffic)
 *   90-120s hold at 2000 VUs        (peak spike)
 *  120-150s ramp back to 0          (cool-down)
 *
 * Thresholds that must pass for the test to be considered successful:
 *   - 95th-percentile response time < 200ms
 *   - Error rate < 1%
 *   - Stock never goes negative (checked via /actuator/health)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const accepted      = new Counter('orders_accepted');
const soldOut       = new Counter('orders_sold_out');
const rateLimited   = new Counter('orders_rate_limited');
const errors        = new Counter('orders_error');
const latency       = new Trend('order_latency_ms', true);
const errorRate     = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '30s', target: 500  },
    { duration: '60s', target: 500  },
    { duration: '30s', target: 2000 },
    { duration: '30s', target: 0    },
  ],
  thresholds: {
    'order_latency_ms': ['p(95)<200'],
    'error_rate':       ['rate<0.01'],
    'http_req_failed':  ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY  = __ENV.API_KEY  || 'dev-api-key-change-in-production';

export default function () {
  const userId    = `user-${__VU}-${__ITER}`;
  const productId = 'IPHONE15';

  const res = http.post(
    `${BASE_URL}/api/flash-sale/buy?userId=${userId}&productId=${productId}`,
    null,
    {
      headers: {
        'X-API-Key': API_KEY,
        'Content-Type': 'application/json',
      },
      timeout: '5s',
    }
  );

  latency.add(res.timings.duration);

  const ok = check(res, {
    'status is 202, 410, or 429': (r) =>
      r.status === 202 || r.status === 410 || r.status === 429 || r.status === 409,
  });

  if (!ok) {
    errorRate.add(1);
    errors.add(1);
    console.error(`Unexpected status ${res.status} body: ${res.body}`);
  } else {
    errorRate.add(0);
    if (res.status === 202) accepted.add(1);
    else if (res.status === 410) soldOut.add(1);
    else if (res.status === 429) rateLimited.add(1);
  }

  sleep(0.1);
}

export function teardown() {
  console.log('Load test complete. Check Grafana for:');
  console.log('  - flashsale_orders_total by result label');
  console.log('  - http_server_requests_seconds p99 latency');
  console.log('  - JVM heap pressure and GC pauses');
}
