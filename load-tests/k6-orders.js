import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const INSTRUMENTED = __ENV.INSTRUMENTED !== "false";
const VUS = Number(__ENV.VUS || 5);
const DURATION = __ENV.DURATION || "30s";
const ORDER_IDS = ["ord-001", "ord-002", "ord-003", "ord-004", "ord-005"];

export const options = {
  scenarios: {
    orders: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
    },
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health/liveness`);
  if (health.status !== 200) {
    throw new Error(`service-a no responde en ${BASE_URL}: HTTP ${health.status}`);
  }
  return { instrumented: INSTRUMENTED };
}

export default function (data) {
  const orderId = ORDER_IDS[Math.floor(Math.random() * ORDER_IDS.length)];
  const response = http.get(`${BASE_URL}/api/orders/${orderId}`, {
    tags: { instrumented: String(data.instrumented) },
    timeout: "10s",
  });

  check(response, {
    "HTTP 200": (r) => r.status === 200,
    "tiene pedido": (r) => {
      try {
        return JSON.parse(r.body).orderId === orderId;
      } catch (error) {
        return false;
      }
    },
    "tiene inventario": (r) => {
      try {
        return JSON.parse(r.body).inventory.productSku !== undefined;
      } catch (error) {
        return false;
      }
    },
    "traceId cuando esta instrumentado": (r) => {
      if (!data.instrumented) {
        return true;
      }
      try {
        const traceId = JSON.parse(r.body).traceId;
        return typeof traceId === "string" && /^[0-9a-f]{32}$/.test(traceId) && !/^0+$/.test(traceId);
      } catch (error) {
        return false;
      }
    },
  });

  sleep(0.1);
}
