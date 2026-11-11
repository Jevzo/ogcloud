/** How proxies pick a backend in a group (D-0131): the network's default, and what NPC clicks may choose. */
export const ROUTING_STRATEGIES = [
  "BALANCED",
  "LEAST_FILLED",
  "MOST_FILLED",
  "ROUND_ROBIN",
] as const;
