import { COMPONENTS, NETWORK_PATTERN } from "./constants";
import type { ComponentName } from "./types";

export function sanitizeNetworkName(network: string | null | undefined): string {
    if (!network) {
        return "";
    }
    return network.trim().toLowerCase();
}

export function validateNetworkName(network: string): boolean {
    return NETWORK_PATTERN.test(network);
}

export function sanitizeComponent(component: string | null | undefined): string {
    if (!component) {
        return "";
    }
    return component.trim().toLowerCase();
}

export function isComponentName(component: string): component is ComponentName {
    return COMPONENTS.includes(component as ComponentName);
}
