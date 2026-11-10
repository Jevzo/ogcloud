import assert from "node:assert/strict";
import { test } from "node:test";
import { Swarm } from "../load/swarm.ts";

const FAKE = new URL("./fixtures/fake-bot-worker.ts", import.meta.url);

test("bots join across the workers, and their later logins are heard", async () => {
  const swarm = Swarm.start(2, FAKE);
  const logins: string[] = [];
  const logged = new Promise<void>((resolve) => {
    swarm.onLogin((name) => {
      logins.push(name);
      if (logins.length === 3) {
        resolve();
      }
    });
  });
  try {
    const joined = await Promise.all(
      ["a", "b", "c"].map((name) => swarm.join(name)),
    );
    await logged;

    assert.deepEqual(joined, [7, 7, 7]);
    assert.deepEqual(logins.toSorted(), ["a", "b", "c"]);
  } finally {
    await swarm.close();
  }
});

test("a bot that cannot join fails with its reason", async () => {
  const swarm = Swarm.start(1, FAKE);
  try {
    await assert.rejects(swarm.join("bad1"), {
      message: "bad1 could not join: kicked",
    });
  } finally {
    await swarm.close();
  }
});

test("a bot whose worker exits fails instead of waiting forever", async () => {
  const swarm = Swarm.start(1, FAKE);
  try {
    await assert.rejects(swarm.join("crash"), {
      message: "crash could not join: its worker exited with 3",
    });
  } finally {
    await swarm.close();
  }
});

// A signal leaves no exit code, and sending to the dead worker would throw.
test("a worker killed by a signal takes no more bots", async () => {
  const swarm = Swarm.start(1, FAKE);
  try {
    await assert.rejects(swarm.join("kill"), {
      message: "kill could not join: its worker exited with SIGKILL",
    });
    await assert.rejects(swarm.join("next"), {
      message: "next has no worker to join on",
    });
    swarm.leave("kill");
  } finally {
    await swarm.close();
  }
});

test("a bot leaves on the worker it joined on", async () => {
  const swarm = Swarm.start(2, FAKE);
  const left = new Promise<string>((resolve) => {
    swarm.onLogin((name) => {
      if (name.endsWith(" left")) {
        resolve(name);
      }
    });
  });
  try {
    await swarm.join("a");
    await swarm.join("b");
    swarm.leave("b");

    assert.equal(await left, "b left");
  } finally {
    await swarm.close();
  }
});
