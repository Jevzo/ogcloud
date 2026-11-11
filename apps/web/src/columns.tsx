import type { columnsFor } from "@ogcloud/ui";
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

/** A sortable column of ids and machine names, in the mono face. */
export function monoColumn<T extends object>(
  columns: Columns<T>,
  id: string,
  header: string,
  value: (row: T) => string,
) {
  return columns.accessor(value, {
    id,
    header: () => header,
    cell: (cell) => (
      <span className="font-mono text-mono text-text-2">{cell.getValue()}</span>
    ),
  });
}
