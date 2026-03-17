import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listAllPermissionGroups, listAllServers } from "@/lib/api";
import type { PermissionGroupRecord } from "@/types/permission";
import type { ServerRecord } from "@/types/server";

interface UsePlayerManagementOptionsQueryOptions {
    canManagePermissionGroups: boolean;
    enabled: boolean;
    permissionSystemEnabled: boolean;
}

interface PlayerManagementOptionsQueryResult {
    errorMessage: string | null;
    isLoading: boolean;
    permissionGroups: PermissionGroupRecord[];
    refresh: () => Promise<void>;
    transferTargets: ServerRecord[];
}

export const usePlayerManagementOptionsQuery = ({
    canManagePermissionGroups,
    enabled,
    permissionSystemEnabled,
}: UsePlayerManagementOptionsQueryOptions): PlayerManagementOptionsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [permissionGroups, setPermissionGroups] = useState<PermissionGroupRecord[]>([]);
    const [transferTargets, setTransferTargets] = useState<ServerRecord[]>([]);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    const refresh = useCallback(async () => {
        if (!enabled) {
            setPermissionGroups([]);
            setTransferTargets([]);
            setErrorMessage(null);
            setIsLoading(false);
            return;
        }

        const requestId = requestIdRef.current + 1;
        requestIdRef.current = requestId;
        setIsLoading(true);

        try {
            const accessToken = await getAccessToken();
            const [nextPermissionGroups, nextServers] = await Promise.all([
                canManagePermissionGroups && permissionSystemEnabled
                    ? listAllPermissionGroups(accessToken)
                    : Promise.resolve<PermissionGroupRecord[]>([]),
                listAllServers(accessToken),
            ]);

            if (requestIdRef.current !== requestId) {
                return;
            }

            setPermissionGroups(
                [...nextPermissionGroups].sort(
                    (left, right) =>
                        left.weight - right.weight || left.name.localeCompare(right.name),
                ),
            );
            setTransferTargets(
                nextServers
                    .filter(
                        (server) =>
                            server.state.toUpperCase() === "RUNNING" &&
                            server.type.toUpperCase() !== "PROXY",
                    )
                    .sort(
                        (left, right) =>
                            left.podName.localeCompare(right.podName) ||
                            left.displayName.localeCompare(right.displayName) ||
                            left.id.localeCompare(right.id),
                    ),
            );
            setErrorMessage(null);
        } catch (error) {
            if (requestIdRef.current !== requestId) {
                return;
            }

            setPermissionGroups([]);
            setTransferTargets([]);
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load player actions.",
            );
        } finally {
            if (requestIdRef.current === requestId) {
                setIsLoading(false);
            }
        }
    }, [canManagePermissionGroups, enabled, getAccessToken, permissionSystemEnabled]);

    useEffect(() => {
        if (!enabled) {
            requestIdRef.current += 1;
            setPermissionGroups([]);
            setTransferTargets([]);
            setErrorMessage(null);
            setIsLoading(false);
            return;
        }

        void refresh();

        return () => {
            requestIdRef.current += 1;
        };
    }, [enabled, refresh]);

    return {
        errorMessage,
        isLoading,
        permissionGroups,
        refresh,
        transferTargets,
    };
};
