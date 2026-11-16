import type { AuditEntryView } from "@ogcloud/api-client";
import {
  useLogAudit,
  usePlayerKnown,
  useUserList,
} from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  EmptyState,
  Input,
  PageHeader,
  Segmented,
} from "@ogcloud/ui";
import {
  Download,
  Eraser,
  FileJson,
  Hourglass,
  type LucideIcon,
  Trash2,
  UserRound,
  UserRoundSearch,
  VenetianMask,
} from "lucide-react";
import { useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { TYPING_PAUSE_MS, useDebounced } from "../../debounce.ts";
import { editorColumns } from "../../editing.tsx";
import { EventItem, EventsCard } from "../../event-item.tsx";
import { isOwner, useCan } from "../../permissions.ts";
import { type PrivacySubject, usePrivacyActions } from "./privacy-actions.tsx";

const MATCHES = 8;
// Enough for a network's dashboard users; the users page pages through everyone.
const USERS_LIMIT = 200;
const RECENT = 6;
// Exports and erasures are audited against this target type (D-0107).
const PRIVACY_TARGET = "privacy";
const ERASURE = /\/erasure$/;

type Privacy = ReturnType<typeof usePrivacyActions>;
type Kind = PrivacySubject["kind"];
type Match = PrivacySubject & { detail: string; lastSeenAt?: string };

/**
 * Finds a player or a dashboard user to export or erase what OgCloud stores about them, beside what each does, and
 * the requests made lately (D-0049, D-0107).
 */
export function PrivacyPage() {
  const { t } = useTranslation("web");
  const privacy = usePrivacyActions();
  const canReadLogs = useCan("logs:read");
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("nav.privacy")} />
      <div className={editorColumns}>
        <div className="flex min-w-0 flex-col gap-6">
          <FindCard privacy={privacy} />
          {canReadLogs ? <RecentCard /> : null}
        </div>
        <EffectsCard />
      </div>
      {privacy.dialogs}
    </div>
  );
}

function FindCard({ privacy }: { privacy: Privacy }) {
  const { t } = useTranslation("web");
  const canReadUsers = useCan("users:read");
  const [kind, setKind] = useState<Kind>("player");
  const [query, setQuery] = useState("");
  const needle = useDebounced(query.trim(), TYPING_PAUSE_MS);
  const players = usePlayerKnown(
    { name: needle, limit: MATCHES },
    { query: { enabled: kind === "player" && needle !== "" } },
  );
  const users = useUserList(
    { limit: USERS_LIMIT },
    { query: { enabled: kind === "user" && canReadUsers } },
  );
  const lower = needle.toLowerCase();
  const matches: Match[] =
    needle === ""
      ? []
      : kind === "player"
        ? (players.data?.data.items ?? []).map((player) => ({
            kind: "player",
            id: player.uuid,
            name: player.name,
            detail: player.uuid,
            lastSeenAt: player.lastSeenAt,
          }))
        : (users.data?.data.items ?? [])
            .filter(
              (user) =>
                user.username.toLowerCase().includes(lower) ||
                user.email.toLowerCase().includes(lower),
            )
            .slice(0, MATCHES)
            .map((user) => ({
              kind: "user",
              id: user.id,
              name: user.username,
              owner: isOwner(user),
              detail: user.email,
            }));
  const searched =
    needle !== "" && (kind === "player" ? players.isSuccess : users.isSuccess);
  const label =
    kind === "player" ? t("privacy.findPlayer") : t("privacy.findUser");

  return (
    <Card
      title={t("privacy.find.title")}
      actions={
        canReadUsers ? (
          <Segmented
            size="sm"
            label={t("privacy.find.kind")}
            options={[
              { value: "player", label: t("privacy.players") },
              { value: "user", label: t("privacy.users") },
            ]}
            value={kind}
            onValueChange={setKind}
          />
        ) : undefined
      }
    >
      <div className="flex flex-col gap-3">
        <Input
          type="search"
          aria-label={label}
          placeholder={label}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {matches.length > 0 ? (
          <ul className="flex flex-col">
            {matches.map((match) => (
              <MatchRow key={match.id} match={match} privacy={privacy} />
            ))}
          </ul>
        ) : searched ? (
          <EmptyState
            icon={UserRoundSearch}
            title={t("privacy.noMatch")}
            description={
              kind === "player"
                ? t("privacy.noMatchDescription")
                : t("privacy.noUserDescription")
            }
          />
        ) : (
          <EmptyState
            icon={UserRoundSearch}
            title={t("privacy.find.idle")}
            description={t("privacy.find.idleDescription")}
          />
        )}
      </div>
    </Card>
  );
}

function MatchRow({ match, privacy }: { match: Match; privacy: Privacy }) {
  const { t } = useTranslation("web");
  return (
    <li className="flex items-center gap-3 border-border-subtle border-t py-2.5 first:border-t-0">
      <span
        aria-hidden="true"
        className="grid size-8 shrink-0 place-items-center rounded-full bg-surface-3 text-text-2"
      >
        <UserRound size={16} strokeWidth={1.75} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="font-label">{match.name}</span>
        <span className="truncate font-mono text-meta text-text-3">
          {match.detail}
        </span>
      </span>
      {match.lastSeenAt === undefined ? null : (
        <span className="shrink-0 text-meta text-text-3">
          <Trans
            t={t}
            i18nKey="privacy.lastSeen"
            components={{ ago: <Ago at={match.lastSeenAt} className="" /> }}
          />
        </span>
      )}
      {privacy.canExport ? (
        <Button
          size="sm"
          icon={Download}
          onClick={() => privacy.exportData(match)}
        >
          {t("privacy.export")}
        </Button>
      ) : null}
      {privacy.mayErase(match) ? (
        // Neutral here; the danger colour waits for the typed confirmation (D-0210).
        <Button size="sm" icon={Eraser} onClick={() => privacy.askErase(match)}>
          {t("privacy.erase")}
        </Button>
      ) : null}
    </li>
  );
}

/** The latest exports and erasures from the audit log, newest first. */
function RecentCard() {
  const { t } = useTranslation("web");
  const audit = useLogAudit({ targetType: PRIVACY_TARGET, limit: RECENT });
  const entries = audit.data?.data.items ?? [];
  return (
    <EventsCard
      title={t("privacy.recent.title")}
      empty={t("privacy.recent.empty")}
      section="audit"
      loading={audit.isPending}
    >
      {entries.map((entry) => (
        <RecentItem key={entry.id} entry={entry} />
      ))}
    </EventsCard>
  );
}

function RecentItem({ entry }: { entry: AuditEntryView }) {
  const { t } = useTranslation("web");
  // The audit names the request, such as "POST /api/v1/privacy/players/{uuid}/erasure".
  const erased = ERASURE.test(entry.action);
  const subject = entry.action.includes("/users/") ? "user" : "player";
  return (
    <EventItem
      icon={erased ? Eraser : Download}
      tint={erased ? "bg-danger-soft text-danger" : "bg-info-soft text-info"}
      title={t(`privacy.recent.${erased ? "erased" : "exported"}.${subject}`)}
      detail={t("privacy.recent.detail", {
        target: entry.targetId ?? "",
        actor: entry.actor,
      })}
      at={entry.occurredAt}
    />
  );
}

const EFFECTS: readonly {
  key: "export" | "deleted" | "pseudonym" | "expires";
  icon: LucideIcon;
}[] = [
  { key: "export", icon: FileJson },
  { key: "deleted", icon: Trash2 },
  { key: "pseudonym", icon: VenetianMask },
  { key: "expires", icon: Hourglass },
];

/** What an export gives and what an erasure deletes, keeps under a pseudonym and leaves to expire. */
function EffectsCard() {
  const { t } = useTranslation("web");
  return (
    <Card title={t("privacy.effects.title")}>
      <ul className="flex flex-col gap-4">
        {EFFECTS.map(({ key, icon: Icon }) => (
          <li key={key} className="flex gap-3">
            <span
              aria-hidden="true"
              className="grid size-8 shrink-0 place-items-center rounded-full bg-surface-3 text-text-2"
            >
              <Icon size={16} strokeWidth={1.75} />
            </span>
            <span className="flex min-w-0 flex-col gap-0.5">
              <span className="font-label">
                {t(`privacy.effects.${key}.title`)}
              </span>
              <span className="text-small text-text-2">
                {t(`privacy.effects.${key}.text`)}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
