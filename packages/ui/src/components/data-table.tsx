import {
  createColumnHelper,
  createSortedRowModel,
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

/** The features every OgCloud table registers: client-side sorting. */
export const tableFeatureSet = tableFeatures({
  rowSortingFeature,
  sortedRowModel: createSortedRowModel(),
  sortFns,
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
const ESTIMATED_ROW_PX = 32;
const OVERSCAN_ROWS = 8;
const LOADING_ROWS = 5;

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
}

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
  const columnCount = table.getAllLeafColumns().length + (actions ? 1 : 0);

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
          <thead className="sticky top-0 z-10 bg-surface">
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => {
                  const sorted = header.column.getIsSorted();
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
                      className="h-row border-border-subtle border-b px-3 text-left font-label text-text-3 whitespace-nowrap first:pl-4"
                    >
                      {header.isPlaceholder ? null : header.column.getCanSort() ? (
                        <button
                          type="button"
                          onClick={header.column.getToggleSortingHandler()}
                          className="inline-flex cursor-pointer items-center gap-1 hover:text-text"
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
                {actions ? (
                  <th
                    scope="col"
                    className="w-px border-border-subtle border-b px-2"
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
                  return (
                    <tr
                      key={row.id}
                      data-index={virtual ? row.index : undefined}
                      ref={virtual ? virtualizer.measureElement : undefined}
                      className={cn(
                        "group hover:bg-surface-2 focus-within:bg-surface-2",
                        highlighted?.(row.original) && "bg-accent-soft/60",
                      )}
                    >
                      {row.getAllCells().map((cell) => (
                        // One line per row, so rows keep the density's height and the virtualiser's measure.
                        <td
                          key={cell.id}
                          className="h-row border-border-subtle border-b px-3 whitespace-nowrap first:pl-4"
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
                      {rowActions ? (
                        <td className="border-border-subtle border-b px-2 text-right whitespace-nowrap">
                          <span className="inline-flex items-center gap-0.5">
                            {rowActions.inline?.map((action) => (
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
