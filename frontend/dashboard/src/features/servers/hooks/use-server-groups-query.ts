import { useCallback, useEffect, useRef, useState } from "react";

import { useAccessToken } from "@/hooks/use-access-token";
import { listAllServerGroups } from "@/lib/api";
import type { GroupRecord } from "@/types/group";

interface ServerGroupsQueryResult {
    data: GroupRecord[];
    errorMessage: string | null;
    isLoading: boolean;
    refresh: () => Promise<void>;
}

export const useServerGroupsQuery = (): ServerGroupsQueryResult => {
    const getAccessToken = useAccessToken();
    const requestIdRef = useRef(0);

    const [data, setData] = useState<GroupRecord[]>([]);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    const refresh = useCallback(async () => {
        const requestId = requestIdRef.current + 1;
        requestIdRef.current = requestId;
        setIsLoading(true);

        try {
            const accessToken = await getAccessToken();
            const nextGroups = await listAllServerGroups(accessToken);

            if (requestIdRef.current !== requestId) {
                return;
            }

            setData(nextGroups);
            setErrorMessage(null);
        } catch (error) {
            if (requestIdRef.current !== requestId) {
                return;
            }

            setErrorMessage(error instanceof Error ? error.message : "Unable to load groups.");
        } finally {
            if (requestIdRef.current === requestId) {
                setIsLoading(false);
            }
        }
    }, [getAccessToken]);

    useEffect(() => {
        void refresh();

        return () => {
            requestIdRef.current += 1;
        };
    }, [refresh]);

    return {
        data,
        errorMessage,
        isLoading,
        refresh,
    };
};
