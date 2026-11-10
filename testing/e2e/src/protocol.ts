// What minecraft-data says about a protocol version, for the bots.

/**
 * The packets a client may send in the configuration state, from a minecraft-data protocol: its
 * configuration.toServer packet type maps ids to names. Empty when the version has no configuration state.
 */
export function configurationPackets(protocol: unknown): ReadonlySet<string> {
  const packet = field(
    protocol,
    "configuration",
    "toServer",
    "types",
    "packet",
  );
  // ["container", [{ name: "name", type: ["mapper", { type: "varint", mappings: { "0x00": "settings" } }] }]]
  const fields = Array.isArray(packet) ? packet[1] : undefined;
  if (!Array.isArray(fields)) {
    return new Set();
  }
  const name = fields.find((entry) => field(entry, "name") === "name");
  const type = field(name, "type");
  const mappings = Array.isArray(type) ? field(type[1], "mappings") : undefined;
  return new Set(
    isRecord(mappings)
      ? Object.values(mappings).filter((value) => typeof value === "string")
      : [],
  );
}

function field(value: unknown, ...path: readonly string[]): unknown {
  let current = value;
  for (const key of path) {
    if (!isRecord(current)) {
      return undefined;
    }
    current = current[key];
  }
  return current;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
