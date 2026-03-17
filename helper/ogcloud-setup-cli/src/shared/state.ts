import * as path from "node:path";

import { NETWORKS_ROOT, STATE_FILE } from "./constants";
import { readJsonFile, saveJsonFile } from "./serialization";
import type { NetworkConfig, StateFile, ValuesPaths } from "./types";

export function valuesPaths(network: string): ValuesPaths {
    const networkDir = path.join(NETWORKS_ROOT, network);
    return {
        config: path.join(networkDir, "config.json"),
        infra: path.join(networkDir, "values.infra.yaml"),
        platform: path.join(networkDir, "values.platform.yaml"),
        dashboard: path.join(networkDir, "values.dashboard.yaml"),
    };
}

export async function loadState(): Promise<StateFile> {
    return readJsonFile(STATE_FILE, {
        lastNetwork: "",
        lastContext: "",
        networks: {},
    });
}

export async function saveState(state: StateFile): Promise<void> {
    await saveJsonFile(STATE_FILE, state);
}

export async function loadNetworkConfig(network: string): Promise<NetworkConfig | null> {
    if (!network) {
        throw new Error("Network name is required.");
    }
    return readJsonFile<NetworkConfig | null>(valuesPaths(network).config, null);
}

export function rememberNetwork(
    state: StateFile,
    network: string,
    namespace: string,
    configPath: string,
    updatedAt: string,
): void {
    state.lastNetwork = network;
    state.networks[network] = {
        namespace,
        configPath,
        updatedAt,
    };
}

export function forgetNetwork(state: StateFile, network: string): void {
    delete state.networks[network];
    if (state.lastNetwork === network) {
        state.lastNetwork = "";
    }
}
