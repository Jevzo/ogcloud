import type { NpcView } from "@ogcloud/api-client";
import { useNpcList } from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  FilterBar,
  PageHeader,
} from "@ogcloud/ui";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, Bot, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { DetailRow } from "../../details.tsx";
import { loadFailure } from "../../errors.ts";
import { useHeld } from "../../held.ts";
import { useCan } from "../../permissions.ts";
import { placeholderNpc } from "../../placeholders.ts";
import { RecordDrawer } from "../../record-drawer.tsx";
import { ALL_GROUPS, useGroupFilter } from "../groups/group-filter.tsx";
import { useBackendGroupIds } from "../groups/group-ids.ts";
import { GroupLink } from "../groups/group-status.tsx";
import { useDeleteNpc } from "./npc-delete.tsx";
import { NpcTitleChip } from "./npc-look.tsx";

const COORDINATE_DIGITS = 1;
const columns = columnsFor<NpcView>();

/** Every managed NPC, by group, with how its title looks and what its clicks do. */
export function NpcsPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const canWrite = useCan("npcs:write");
  const groupIds = useBackendGroupIds();
  const { group, filter: groupFilter } = useGroupFilter(groupIds);
  const npcs = useNpcList(
    group === ALL_GROUPS ? undefined : { groupId: group },
  );
  const remove = useDeleteNpc();
  const [opened, setOpened] = useState<NpcView | null>(null);
  // The drawer follows the list's refreshes and slides out once its NPC is gone.
  const current =
    opened === null
      ? null
      : ((npcs.data?.data ?? []).find((npc) => npc.id === opened.id) ?? null);
  const npc = useHeld(current);
  const open = (id: string) =>
    navigate({ to: "/npcs/$npcId", params: { npcId: id } });

  const npcColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("settings.title", {
          header: () => t("npcs.columns.title"),
          cell: (cell) => (
            <Link
              to="/npcs/$npcId"
              params={{ npcId: cell.row.original.id }}
              aria-label={t("npcs.open")}
            >
              <NpcTitleChip title={cell.getValue()} />
            </Link>
          ),
        }),
        columns.accessor("settings.groupId", {
          header: () => t("npcs.columns.group"),
          cell: (cell) => <GroupLink id={cell.getValue()} />,
        }),
        columns.accessor("settings.location", {
          header: () => t("npcs.columns.location"),
          enableSorting: false,
          cell: (cell) => (
            <span className="font-mono text-mono text-text-2">
              {placeOf(cell.getValue())}
            </span>
          ),
        }),
        columns.accessor(
          (npc) =>
            npc.settings.rightClick?.groupId ??
            npc.settings.leftClick?.groupId ??
            null,
          {
            id: "click",
            header: () => t("npcs.columns.click"),
            cell: (cell) => {
              const target = cell.getValue();
              return (
                <span className="text-text-2">
                  {target === null
                    ? t("npcs.form.nothing")
                    : t("npcs.form.moveTo", { group: target })}
                </span>
              );
            },
          },
        ),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("nav.npcs")}
        actions={
          canWrite ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => navigate({ to: "/npcs/new" })}
            >
              {t("npcs.create.title")}
            </Button>
          ) : undefined
        }
      />
      <FilterBar filters={[groupFilter]} />
      <Card flush>
        <DataTable
          label={t("nav.npcs")}
          data={npcs.data?.data ?? []}
          failure={loadFailure(npcs)}
          columns={npcColumns}
          getRowId={(npc) => npc.id}
          placeholder={placeholderNpc}
          actions={(npc) => ({
            menu: [
              [
                {
                  label: t("npcs.open"),
                  icon: ArrowUpRight,
                  onSelect: () => open(npc.id),
                },
              ],
              ...(canWrite
                ? [
                    [
                      {
                        label: t("npcs.delete.action"),
                        icon: Trash2,
                        danger: true,
                        onSelect: () => remove.ask(npc.id),
                      },
                    ],
                  ]
                : []),
            ],
          })}
          onRowOpen={setOpened}
          loading={npcs.isPending}
          empty={
            <EmptyState
              icon={Bot}
              title={t("npcs.empty")}
              description={t("npcs.emptyDescription")}
            />
          }
        />
      </Card>
      {npc === null ? null : (
        <RecordDrawer
          open={current !== null}
          title={t("npcs.detail.title", { group: npc.settings.groupId })}
          onClose={() => setOpened(null)}
          onOpenPage={() => void open(npc.id)}
        >
          <DetailRow label={t("npcs.columns.title")}>
            <NpcTitleChip title={npc.settings.title} />
          </DetailRow>
          <DetailRow label={t("npcs.columns.group")}>
            <GroupLink id={npc.settings.groupId} />
          </DetailRow>
          <DetailRow label={t("npcs.columns.location")}>
            <span className="font-mono text-mono">
              {placeOf(npc.settings.location)}
            </span>
          </DetailRow>
          <DetailRow label={t("npcs.columns.click")}>
            {npc.settings.rightClick?.groupId == null
              ? t("npcs.form.nothing")
              : t("npcs.form.moveTo", {
                  group: npc.settings.rightClick.groupId,
                })}
          </DetailRow>
        </RecordDrawer>
      )}
      {remove.dialog}
    </div>
  );
}

/** Where an NPC stands: its world, then x, y and z to a tenth of a block. */
function placeOf({ world, x, y, z }: NpcView["settings"]["location"]): string {
  return `${world} ${[x, y, z].map((n) => n.toFixed(COORDINATE_DIGITS)).join(" ")}`;
}
