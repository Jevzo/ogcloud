export const COMMAND_TARGET_TYPES = ["server", "group", "all"] as const;

export type CommandTargetType = (typeof COMMAND_TARGET_TYPES)[number];

export interface ExecuteCommandPayload {
    target: string;
    targetType: CommandTargetType;
    command: string;
}
