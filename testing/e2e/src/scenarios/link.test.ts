import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { after, test } from "node:test";
import {
  meConfirmMinecraftLink,
  meGet,
  meRequestMinecraftLink,
  userCreate,
  userDelete,
} from "@ogcloud/api-client";
import { as, expectStatus } from "../api.ts";
import { join, message } from "../bots.ts";
import { signIn } from "../login.ts";
import { playerName, scenario } from "../suite.ts";

// The Minecraft account link (D-0106): the player reads a code in chat and the web user confirms it. A linked user's
// username follows the player's name, so the scenario links a user of its own, never the seed's owner.
const { context, players } = scenario();
let userId: string | undefined;

after(async () => {
  if (userId !== undefined) {
    expectStatus(
      await userDelete(userId, context.api),
      204,
      "Deleting the linked user",
    );
  }
});

test("a web user links their Minecraft account with the code the player reads in chat", async () => {
  const owner = await signIn(context.target, context.state.owner);
  const login = {
    username: playerName("web"),
    password: randomBytes(24).toString("base64url"),
  };
  const created = expectStatus(
    await userCreate(
      {
        ...login,
        email: `${login.username}@ogcloud.localhost`,
        roleId: "viewer",
      },
      as(context.target, owner.accessToken),
    ),
    201,
    "Creating a viewer",
  );
  userId = created.id;
  const user = as(
    context.target,
    (await signIn(context.target, login)).accessToken,
  );

  const name = playerName("link");
  const player = await join(context.target, name);
  players.push(player);
  expectStatus(
    await meRequestMinecraftLink({ playerName: name }, user),
    202,
    "Requesting the link code",
  );
  const [code = ""] = await message(player, /\b\d{6}\b/);
  expectStatus(
    await meConfirmMinecraftLink({ code }, user),
    200,
    "Confirming the link code",
  );

  const me = expectStatus(await meGet(user), 200, "Reading the linked user");
  assert.equal(me.linkedPlayerName, name);
});
