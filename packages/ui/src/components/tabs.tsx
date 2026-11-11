import { Tabs as Primitive } from "radix-ui";
import type { ReactNode } from "react";

export interface Tab<T extends string> {
  value: T;
  label: string;
  content: ReactNode;
}

interface TabsProps<T extends string> {
  label: string;
  tabs: readonly Tab<T>[];
  value: T;
  onValueChange: (value: T) => void;
}

/** Sections of one page that are worked on separately, such as a group's settings and its templates. */
export function Tabs<T extends string>({
  label,
  tabs,
  value,
  onValueChange,
}: TabsProps<T>) {
  return (
    <Primitive.Root
      value={value}
      onValueChange={(next) => onValueChange(next as T)}
    >
      <Primitive.List
        aria-label={label}
        className="mb-4 flex gap-1 border-border-subtle border-b"
      >
        {tabs.map((tab) => (
          <Primitive.Trigger
            key={tab.value}
            value={tab.value}
            className="-mb-px cursor-pointer border-transparent border-b-2 px-3 pb-2 font-label text-text-2 outline-none transition-colors hover:text-text focus-visible:text-text data-[state=active]:border-accent data-[state=active]:text-text"
          >
            {tab.label}
          </Primitive.Trigger>
        ))}
      </Primitive.List>
      {tabs.map((tab) => (
        <Primitive.Content
          key={tab.value}
          value={tab.value}
          className="outline-none"
        >
          {tab.content}
        </Primitive.Content>
      ))}
    </Primitive.Root>
  );
}
