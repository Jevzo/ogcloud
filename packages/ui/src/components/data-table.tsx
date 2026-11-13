import { defaultDensity, densities } from "@ogcloud/tokens";
import {
  createColumnHelper,
  createSortedRowModel,
  metaHelper,
  type RowData,
  rowSortingFeature,
  sortFns,
  tableFeatures,
  useTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import {
  ArrowDown,
  ArrowUp,
  Ellipsis,
  type LucideIcon,
  PanelRight,
  RotateCw,
  TriangleAlert,
} from "lucide-react";
import { type ReactNode, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "../lib/cn.ts";
import { Button } from "./button.tsx";
import { EmptyState } from "./layout.tsx";
import { Loading, useLoading, Value } from "./loading.tsx";
import { Menu, type MenuItem } from "./menu.tsx";

/** What a column may say about itself to the table. */
export interface ColumnMeta {
  /** Counts, sizes and ratios: right-aligned with tabular figures, head included (D-0231). */
  numeric?: boolean;
}

/** The features every OgCloud table registers: client-side sorting. */
export const tableFeatureSet = tableFeatures({
  rowSortingFeature,
  sortedRowModel: createSortedRowModel(),
  sortFns,
  columnMeta: metaHelper<ColumnMeta>(),
});

export const columnsFor = <T extends RowData>() =>
  createColumnHelper<typeof tableFeatureSet, T>();

type Columns<T extends RowData> = ReturnType<
  ReturnType<typeof columnsFor<T>>["columns"]
>;

export interface RowAction {
  label: string;
  icon: LucideIcon;
  onPress: () => void;
}

export interface RowActions {
  /** The row's most frequent actions as icon buttons, shown on hover and keyboard focus (D-0210). */
  inline?: readonly RowAction[];
  /** Everything, in the ⋯ menu that every row shows. */
  menu: readonly (readonly MenuItem[])[];
}

// Long lists (players, servers at scale) render only the rows in view.
const VIRTUALIZE_FROM = 100;
const ESTIMATED_ROW_PX = densities[defaultDensity].rowHeight;
const OVERSCAN_ROWS = 8;
const LOADING_ROWS = 5;
// The head's band; each cell reaches one pixel into the next, so fractional column widths leave no seams.
const HEAD_CELL = "bg-surface-3 shadow-[1px_0_0_var(--og-surface-3)]";

/** Why a list could not load, and a way to try again; shown where the rows would be. */
export interface TableFailure {
  message: string;
  retry: () => void;
}

interface DataTableProps<T extends RowData> {
  label: string;
  data: readonly T[];
  columns: Columns<T>;
  getRowId: (row: T) => string;
  actions?: ((row: T) => RowActions) | undefined;
  loading?: boolean;
  /** A row as the contract shapes it, drawn as skeleton rows while the real ones load (D-0228). */
  placeholder: T;
  empty: ReactNode;
  /** Set while the rows failed to load; with rows already shown, they stay. */
  failure?: TableFailure | undefined;
  /** The height long lists scroll within, e.g. `calc(100vh - 14rem)`. */
  maxHeight?: string;
  /** Rows to mark, such as one that changed state just now. */
  highlighted?: (row: T) => boolean;
  /** Opens a row's drawer: a click on the row, or its details button for the keyboard (D-0231). */
  onRowOpen?: ((row: T) => void) | undefined;
}

// Clicks on the row's own links and buttons do what they say, not open the drawer.
const INTERACTIVE = "a, button, input, select, textarea, [role=menuitem]";

export function DataTable<T extends RowData>({
  label,
  data,
  columns,
  getRowId,
  actions,
  loading: loadingRows = false,
  placeholder,
  empty,
  failure,
  maxHeight,
  highlighted,
  onRowOpen,
}: DataTableProps<T>) {
  const { t } = useTranslation("ui");
  // A loading view passes its own placeholder rows; a table loading alone draws a few of its placeholder.
  const loading = useLoading() || loadingRows;
  const placeholders = useMemo(
    () => Array.from({ length: LOADING_ROWS }, () => placeholder),
    [placeholder],
  );
  const table = useTable({
    features: tableFeatureSet,
    columns,
    data: (loadingRows ? placeholders : data) as T[],
    getRowId: (row, index) => (loading ? `loading-${index}` : getRowId(row)),
  });
  const scroller = useRef<HTMLDivElement>(null);
  const rows = table.getRowModel().rows;
  const virtual = rows.length >= VIRTUALIZE_FROM && maxHeight !== undefined;
  const virtualizer = useVirtualizer({
    count: virtual ? rows.length : 0,
    getScrollElement: () => scroller.current,
    estimateSize: () => ESTIMATED_ROW_PX,
    getItemKey: (index) => rows[index]?.id ?? index,
    overscan: OVERSCAN_ROWS,
  });
  const items = virtualizer.getVirtualItems();
  const visible = virtual
    ? items.flatMap((item) => rows[item.index] ?? [])
    : rows;
  const paddingTop = virtual ? (items[0]?.start ?? 0) : 0;
  const paddingBottom = virtual
    ? virtualizer.getTotalSize() - (items.at(-1)?.end ?? 0)
    : 0;
  const hasActions = actions !== undefined || onRowOpen !== undefined;
  const columnCount = table.getAllLeafColumns().length + (hasActions ? 1 : 0);

  return (
    <Loading when={loading}>
      <div
        ref={scroller}
        className="overflow-auto"
        style={maxHeight === undefined ? undefined : { maxHeight }}
      >
        <table
          aria-label={label}
          className="w-full border-collapse text-table tabular-nums"
        >
          <thead className="sticky top-0 z-10">
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => {
                  const sorted = header.column.getIsSorted();
                  const numeric = header.column.columnDef.meta?.numeric;
                  return (
                    <th
                      key={header.id}
                      scope="col"
                      aria-sort={
                        sorted === "asc"
                          ? "ascending"
                          : sorted === "desc"
                            ? "descending"
                            : undefined
                      }
                      className={cn(
                        HEAD_CELL,
                        "h-row border-border-subtle border-b px-3 font-label text-text-3 whitespace-nowrap first:pl-4",
                        numeric ? "text-right" : "text-left",
                      )}
                    >
                      {header.isPlaceholder ? null : header.column.getCanSort() ? (
                        <button
                          type="button"
                          onClick={header.column.getToggleSortingHandler()}
                          className={cn(
                            "inline-flex min-h-6 cursor-pointer items-center gap-1 hover:text-text",
                            // A right-aligned head keeps its label on the column's edge and its arrow inside.
                            numeric && "flex-row-reverse",
                          )}
                        >
                          <table.FlexRender header={header} />
                          {sorted === "asc" ? (
                            <ArrowUp
                              aria-label={t("table.sortedAscending")}
                              size={13}
                            />
                          ) : sorted === "desc" ? (
                            <ArrowDown
                              aria-label={t("table.sortedDescending")}
                              size={13}
                            />
                          ) : null}
                        </button>
                      ) : (
                        <table.FlexRender header={header} />
                      )}
                    </th>
                  );
                })}
                {hasActions ? (
                  <th
                    scope="col"
                    className={cn(
                      HEAD_CELL,
                      "w-px border-border-subtle border-b px-2",
                    )}
                  >
                    <span className="sr-only">{t("table.actions")}</span>
                  </th>
                ) : null}
              </tr>
            ))}
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columnCount} className="p-4">
                  {failure === undefined ? (
                    empty
                  ) : (
                    <EmptyState
                      icon={TriangleAlert}
                      title={t("table.failed")}
                      description={failure.message}
                      action={
                        <Button icon={RotateCw} onClick={failure.retry}>
                          {t("table.retry")}
                        </Button>
                      }
                    />
                  )}
                </td>
              </tr>
            ) : (
              <>
                {paddingTop > 0 ? <tr style={{ height: paddingTop }} /> : null}
                {visible.map((row) => {
                  const rowActions = actions?.(row.original);
                  const open =
                    onRowOpen === undefined || loading
                      ? undefined
                      : () => onRowOpen(row.original);
                  return (
                    <tr
                      key={row.id}
                      data-index={virtual ? row.index : undefined}
                      ref={virtual ? virtualizer.measureElement : undefined}
                      onClick={
                        open === undefined
                          ? undefined
                          : (event) => {
                              if (
                                event.target instanceof Element &&
                                event.target.closest(INTERACTIVE) === null
                              ) {
                                open();
                              }
                            }
                      }
                      className={cn(
                        "group hover:bg-surface-2 focus-within:bg-surface-2",
                        open !== undefined && "cursor-pointer",
                        highlighted?.(row.original) && "bg-accent-soft/60",
                      )}
                    >
                      {row.getAllCells().map((cell) => (
                        // One line per row, so rows keep the density's height and the virtualiser's measure.
                        <td
                          key={cell.id}
                          className={cn(
                            "h-row border-border-subtle border-b px-3 whitespace-nowrap first:pl-4",
                            cell.column.columnDef.meta?.numeric && "text-right",
                          )}
                        >
                          {loading ? (
                            <Value className="align-middle">
                              <table.FlexRender cell={cell} />
                            </Value>
                          ) : (
                            <table.FlexRender cell={cell} />
                          )}
                        </td>
                      ))}
                      {hasActions ? (
                        <td className="border-border-subtle border-b px-2 text-right whitespace-nowrap">
                          <span className="inline-flex items-center gap-0.5">
                            {open === undefined ? null : (
                              <Button
                                kind="ghost"
                                size="sm"
                                icon={PanelRight}
                                iconOnly
                                onClick={open}
                                className="opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100"
                              >
                                {t("table.details")}
                              </Button>
                            )}
                            {rowActions?.inline?.map((action) => (
                              <Button
                                key={action.label}
                                kind="ghost"
                                size="sm"
                                icon={action.icon}
                                iconOnly
                                onClick={action.onPress}
                                className="opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100"
                              >
                                {action.label}
                              </Button>
                            ))}
                            {rowActions === undefined ? null : (
                              <Menu
                                groups={rowActions.menu}
                                trigger={
                                  <Button
                                    kind="ghost"
                                    size="sm"
                                    icon={Ellipsis}
                                    iconOnly
                                  >
                                    {t("actions.more")}
                                  </Button>
                                }
                              />
                            )}
                          </span>
                        </td>
                      ) : null}
                    </tr>
                  );
                })}
                {paddingBottom > 0 ? (
                  <tr style={{ height: paddingBottom }} />
                ) : null}
              </>
            )}
          </tbody>
        </table>
      </div>
    </Loading>
  );
}
