import type {
  TextErrorView,
  TextPreview,
  TextPreviewRequestField,
} from "@ogcloud/api-client";
import { useTextPreviewHook } from "@ogcloud/api-client/react";
import {
  keepPreviousData,
  type QueryClient,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type { TFunction } from "i18next";
import { useTranslation } from "react-i18next";
import { useDebounced } from "./debounce.ts";

// In-game texts are checked and drawn by the API, never parsed here (D-0202).

const DEBOUNCE_MS = 300;
const PREVIEW_KEY = "text-preview";

type Preview = (
  field: TextPreviewRequestField,
  text: string,
) => Promise<TextPreview>;

function usePreview(): Preview {
  const preview = useTextPreviewHook();
  return async (field, text) => (await preview({ field, text })).data;
}

function cached(
  client: QueryClient,
  preview: Preview,
  field: TextPreviewRequestField,
  text: string,
) {
  return client.fetchQuery({
    queryKey: [PREVIEW_KEY, field, text],
    queryFn: () => preview(field, text),
    staleTime: Number.POSITIVE_INFINITY,
  });
}

/** How a text looks to modern and older clients, or what keeps it from being saved; asked once the typing pauses. */
export function useTextPreview(field: TextPreviewRequestField, text: string) {
  const preview = usePreview();
  const settled = useDebounced(text, DEBOUNCE_MS);
  return useQuery({
    queryKey: [PREVIEW_KEY, field, settled],
    queryFn: () => preview(field, settled),
    placeholderData: keepPreviousData,
    staleTime: Number.POSITIVE_INFINITY,
  });
}

/** A field validator that refuses texts the API refuses, so a form cannot save them (D-0205). */
export function useTextValidator(field: TextPreviewRequestField) {
  const { t } = useTranslation("web");
  const client = useQueryClient();
  const preview = usePreview();
  return {
    onChangeAsyncDebounceMs: DEBOUNCE_MS,
    onChangeAsync: async ({ value }: { value: string }) => {
      const result = await cached(client, preview, field, value);
      const first = result.errors[0];
      return first === undefined ? undefined : textProblem(t, first);
    },
  };
}

/** A text problem in the catalog's words, from its code (D-0205). */
export function textProblem(t: TFunction<"web">, error: TextErrorView): string {
  return t(`texts.errors.${error.code}`);
}
