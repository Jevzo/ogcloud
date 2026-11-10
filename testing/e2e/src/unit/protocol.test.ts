import assert from "node:assert/strict";
import { test } from "node:test";
import { configurationPackets } from "../protocol.ts";

test("the configuration packets come from the protocol's id mapping", () => {
  const protocol = {
    configuration: {
      toServer: {
        types: {
          packet: [
            "container",
            [
              {
                name: "name",
                type: [
                  "mapper",
                  {
                    type: "varint",
                    mappings: {
                      "0x00": "settings",
                      "0x02": "finish_configuration",
                    },
                  },
                ],
              },
              { name: "params", type: ["switch", {}] },
            ],
          ],
        },
      },
    },
  };

  assert.deepEqual(
    [...configurationPackets(protocol)],
    ["settings", "finish_configuration"],
  );
});

test("a version without a configuration state has no configuration packets", () => {
  assert.equal(configurationPackets({ play: {} }).size, 0);
  assert.equal(configurationPackets(undefined).size, 0);
});
