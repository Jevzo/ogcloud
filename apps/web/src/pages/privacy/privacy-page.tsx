import { usePlayerKnown, useUserList } from "@ogcloud/api-client/react";
import { Button, Card, EmptyState, Input, Note, PageHeader } from "@ogcloud/ui";
import { Download, Eraser, Info, UserRoundSearch } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { TYPING_PAUSE_MS, useDebounced } from "../../debounce.ts";
import { isOwner, useCan } from "../../permissions.ts";
import { type PrivacySubject, usePrivacyActions } from "./privacy-actions.tsx";

const MATCHES = 8;
// Enough for a network's dashboard users; the users page pages through everyone.
const USERS_LIMIT = 200;

/** Finds a player or a dashboard user and exports or erases what OgCloud stores about them (D-0049, D-0107). */
export function PrivacyPage() {
  const { t } = useTranslation("web");
  const privacy = usePrivacyActions();
  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.privacy")}
        description={t("privacy.description")}
      />
      <Note tone="info" icon={Info}>
        {t("privacy.kept")}
      </Note>
      <div className="grid grid-cols-2 items-start gap-4">
        <PlayerSearch privacy={privacy} />
        <UserSearch privacy={privacy} />
      </div>
      {privacy.dialogs}
    </div>
  );
}

type Privacy = ReturnType<typeof usePrivacyActions>;

function PlayerSearch({ privacy }: { privacy: Privacy }) {
  const { t } = useTranslation("web");
  const [query, setQuery] = useState("");
  const name = useDebounced(query.trim(), TYPING_PAUSE_MS);
  const players = usePlayerKnown(
    { name, limit: MATCHES },
    { query: { enabled: name !== "" } },
  );
  const matches = name === "" ? [] : (players.data?.data.items ?? []);
  return (
    <SearchCard
      title={t("privacy.players")}
      label={t("privacy.findPlayer")}
      query={query}
      onQuery={setQuery}
      subjects={matches.map((player) => ({
        kind: "player",
        id: player.uuid,
        name: player.name,
        detail: player.uuid,
      }))}
      searched={name !== "" && players.isSuccess}
      privacy={privacy}
    />
  );
}

function UserSearch({ privacy }: { privacy: Privacy }) {
  const { t } = useTranslation("web");
  const canRead = useCan("users:read");
  const [query, setQuery] = useState("");
  const users = useUserList(
    { limit: USERS_LIMIT },
    { query: { enabled: canRead } },
  );
  if (!canRead) return null;
  const needle = query.trim().toLowerCase();
  const matches =
    needle === ""
      ? []
      : (users.data?.data.items ?? [])
          .filter(
            (user) =>
              user.username.toLowerCase().includes(needle) ||
              user.email.toLowerCase().includes(needle),
          )
          .slice(0, MATCHES);
  return (
    <SearchCard
      title={t("privacy.users")}
      label={t("privacy.findUser")}
      query={query}
      onQuery={setQuery}
      subjects={matches.map((user) => ({
        kind: "user",
        id: user.id,
        name: user.username,
        owner: isOwner(user),
        detail: user.email,
      }))}
      searched={needle !== "" && users.isSuccess}
      privacy={privacy}
    />
  );
}

function SearchCard({
  title,
  label,
  query,
  onQuery,
  subjects,
  searched,
  privacy,
}: {
  title: string;
  label: string;
  query: string;
  onQuery: (query: string) => void;
  subjects: (PrivacySubject & { detail: string })[];
  searched: boolean;
  privacy: Privacy;
}) {
  const { t } = useTranslation("web");
  return (
    <Card title={title}>
      <div className="flex flex-col gap-3">
        <Input
          aria-label={label}
          placeholder={label}
          type="search"
          value={query}
          onChange={(event) => onQuery(event.target.value)}
        />
        {subjects.length === 0 ? (
          searched ? (
            <EmptyState
              icon={UserRoundSearch}
              title={t("privacy.noMatch")}
              description={t("privacy.noMatchDescription")}
            />
          ) : null
        ) : (
          <ul className="flex flex-col">
            {subjects.map((subject) => (
              <li
                key={subject.id}
                className="flex items-center gap-3 border-border-subtle border-t py-2 first:border-t-0"
              >
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="font-label">{subject.name}</span>
                  <span className="truncate font-mono text-meta text-text-3">
                    {subject.detail}
                  </span>
                </span>
                {privacy.canExport ? (
                  <Button
                    size="sm"
                    icon={Download}
                    onClick={() => privacy.exportData(subject)}
                  >
                    {t("privacy.export")}
                  </Button>
                ) : null}
                {privacy.mayErase(subject) ? (
                  <Button
                    size="sm"
                    kind="danger"
                    icon={Eraser}
                    onClick={() => privacy.askErase(subject)}
                  >
                    {t("privacy.erase")}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
