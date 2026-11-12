import { cn, type columnsFor } from "@ogcloud/ui";
import { Ago } from "./ago.tsx";

type Columns<T extends object> = ReturnType<typeof columnsFor<T>>;

/** A sortable column of moments, shown as how long ago they were. */
export function agoColumn<T extends object>(
  columns: Columns<T>,
  id: string,
  header: string,
  at: (row: T) => string | null | undefined,
) {
  return columns.accessor(at, {
    id,
    header: () => header,
    cell: (cell) => <Ago at={cell.getValue()} />,
  });
}

/**
 * A sortable column of ids and machine names, in the mono face. A [truncated] column cuts long values with an
 * ellipsis and shows them in full on hover, for UUIDs that would otherwise push the table wider than the page.
 */
export function monoColumn<T extends object>(
  columns: Columns<T>,
  id: string,
  header: string,
  value: (row: T) => string,
  truncated = false,
) {
  return columns.accessor(value, {
    id,
    header: () => header,
    cell: (cell) => (
      <span
        title={truncated ? cell.getValue() : undefined}
        className={cn(
          "font-mono text-mono text-text-2",
          truncated && "block max-w-64 truncate",
        )}
      >
        {cell.getValue()}
      </span>
    ),
  });
}
