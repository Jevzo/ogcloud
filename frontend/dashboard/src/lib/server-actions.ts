import { forceServerTemplatePush, killServerInstance, stopServerGracefully } from "@/lib/api";
import type { ServerActionKind } from "@/types/server";

const ACTION_EXECUTORS: Record<
    ServerActionKind,
    (accessToken: string, serverId: string) => Promise<void>
> = {
    drain: stopServerGracefully,
    push: forceServerTemplatePush,
    kill: killServerInstance,
};

const ACTION_SUCCESS_MESSAGE: Record<ServerActionKind, string> = {
    drain: "Drain requested",
    push: "Template push requested",
    kill: "Kill requested",
};

export const runServerAction = async (
    accessToken: string,
    serverId: string,
    action: ServerActionKind,
) => {
    const executeAction = ACTION_EXECUTORS[action];
    await executeAction(accessToken, serverId);
};

export const getServerActionSuccessMessage = (serverId: string, action: ServerActionKind) =>
    `${ACTION_SUCCESS_MESSAGE[action]} for ${serverId}.`;
