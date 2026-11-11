import { type ThemeName, themes, type typeScale } from "@ogcloud/tokens";
import {
  CircleCheck,
  CirclePause,
  LoaderCircle,
  Plus,
  RotateCcw,
  Skull,
  Square,
  TriangleAlert,
  Upload,
} from "lucide-react";
import { type ReactNode, useState } from "react";
import {
  Badge,
  Button,
  type ButtonKind,
  Card,
  ChatPreview,
  Checkbox,
  ColourPicker,
  Combobox,
  ConfirmDialog,
  CopyField,
  columnsFor,
  DataTable,
  DateTimePicker,
  Dialog,
  EmptyState,
  Field,
  type GameSpan,
  Input,
  Meter,
  MultiSelect,
  minecraftColours,
  NametagPreview,
  Note,
  PageHeader,
  PreviewFrame,
  RadioGroup,
  SaveBar,
  Segmented,
  Select,
  ServerListPreview,
  StackedBar,
  Switch,
  TablistPreview,
  TagTextField,
  TimeSeriesChart,
  type Tone,
  useToast,
} from "../src/index.ts";

// One fixed moment, so every screenshot shows the same dates.
const NOW = new Date("2026-11-11T18:40:00");
const ICON =
  "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 8 8'><rect width='8' height='8' fill='%236E4DF5'/><rect x='2' y='2' width='4' height='4' fill='%23FFAA00'/></svg>";

interface ServerRow {
  id: string;
  group: string;
  state: string;
  tone: Tone;
  players: number;
  max: number;
  game: string;
  started: string;
}

const SERVERS: ServerRow[] = [
  {
    id: "lobby-3f2a9c",
    group: "lobby",
    state: "Running",
    tone: "success",
    players: 82,
    max: 100,
    game: "Lobby",
    started: "2 h ago",
  },
  {
    id: "bedwars-5c1e09",
    group: "bedwars",
    state: "Starting",
    tone: "info",
    players: 0,
    max: 64,
    game: "Lobby",
    started: "1 min ago",
  },
  {
    id: "bedwars-e0b2d4",
    group: "bedwars",
    state: "Running",
    tone: "success",
    players: 61,
    max: 64,
    game: "In game",
    started: "14 min ago",
  },
  {
    id: "lobby-a41c07",
    group: "lobby",
    state: "Draining",
    tone: "warning",
    players: 3,
    max: 100,
    game: "Lobby",
    started: "5 h ago",
  },
  {
    id: "smp-9d27f1",
    group: "smp",
    state: "Running",
    tone: "success",
    players: 41,
    max: 100,
    game: "",
    started: "3 d ago",
  },
  {
    id: "proxy-0c19f4",
    group: "proxy",
    state: "Running",
    tone: "success",
    players: 642,
    max: 1000,
    game: "",
    started: "6 h ago",
  },
];

const STATE_ICONS = {
  success: CircleCheck,
  info: LoaderCircle,
  warning: CirclePause,
  danger: TriangleAlert,
} as const;

const column = columnsFor<ServerRow>();
const SERVER_COLUMNS = column.columns([
  column.accessor("id", {
    header: "Server",
    cell: (cell) => (
      <span className="font-label font-mono">{cell.getValue()}</span>
    ),
  }),
  column.accessor("group", {
    header: "Group",
    cell: (cell) => <span className="text-text-2">{cell.getValue()}</span>,
  }),
  column.accessor("state", {
    header: "State",
    cell: (cell) => {
      const { tone } = cell.row.original;
      const icon =
        tone in STATE_ICONS
          ? STATE_ICONS[tone as keyof typeof STATE_ICONS]
          : undefined;
      return icon === undefined ? (
        <Badge tone={tone}>{cell.getValue()}</Badge>
      ) : (
        <Badge tone={tone} icon={icon}>
          {cell.getValue()}
        </Badge>
      );
    },
  }),
  column.accessor("players", {
    header: "Players",
    cell: (cell) => (
      <span className="inline-flex items-center gap-2">
        <Meter value={cell.getValue()} max={cell.row.original.max} />
        {cell.getValue()} / {cell.row.original.max}
      </span>
    ),
  }),
  column.accessor("game", {
    header: "Game",
    cell: (cell) => <span className="text-text-2">{cell.getValue()}</span>,
  }),
  column.accessor("started", {
    header: "Started",
    enableSorting: false,
    cell: (cell) => <span className="text-text-2">{cell.getValue()}</span>,
  }),
]);

const GROUPS = [
  { value: "lobby", label: "lobby", hint: "Dynamic" },
  { value: "bedwars", label: "bedwars", hint: "Dynamic" },
  { value: "skywars", label: "skywars", hint: "Dynamic" },
  { value: "smp", label: "smp", hint: "Static" },
  { value: "build", label: "build", hint: "Static" },
] as const;

const SCOPES = [
  "servers:read",
  "servers:command",
  "groups:read",
  "groups:write",
  "players:read",
  "players:write",
  "privacy:export",
].map((scope) => ({ value: scope, label: scope }));

function hex(channels: number[]): string {
  return `#${channels.map((c) => Math.round(c).toString(16).padStart(2, "0")).join("")}`;
}

function rgb(colour: string): number[] {
  return [1, 3, 5].map((i) => Number.parseInt(colour.slice(i, i + 2), 16));
}

function nearestNamed(colour: string): string {
  const [r = 0, g = 0, b = 0] = rgb(colour);
  return Object.values(minecraftColours).reduce((best, candidate) => {
    const [r2 = 0, g2 = 0, b2 = 0] = rgb(candidate);
    const [r3 = 0, g3 = 0, b3 = 0] = rgb(best);
    const distance = 2 * (r - r2) ** 2 + 4 * (g - g2) ** 2 + 3 * (b - b2) ** 2;
    const bestDistance =
      2 * (r - r3) ** 2 + 4 * (g - g3) ** 2 + 3 * (b - b3) ** 2;
    return distance < bestDistance ? candidate : best;
  });
}

/** Sample spans as the API would return them, a colour per letter of the gradient. */
function gradient(
  text: string,
  from: string,
  to: string,
  legacy: boolean,
  bold = false,
): GameSpan[] {
  const [a, b] = [rgb(from), rgb(to)];
  const letters = Array.from(text);
  return letters.map((letter, index) => {
    const mix = hex(
      (a ?? []).map(
        (channel, k) =>
          channel +
          ((b?.[k] ?? 0) - channel) * (index / Math.max(1, letters.length - 1)),
      ),
    );
    return { text: letter, color: legacy ? nearestNamed(mix) : mix, bold };
  });
}

function motd(legacy: boolean): GameSpan[][] {
  return [
    [
      ...gradient("OgWars", "#FF8800", "#FF3D7F", legacy, true),
      { text: " Bedwars, SkyWars and more", color: minecraftColours.gray },
    ],
    [
      { text: "New: ", color: minecraftColours.yellow },
      { text: "SMP season 4 is open", color: minecraftColours.white },
    ],
  ];
}

function playersSeries() {
  return Array.from({ length: 145 }, (_, index) => {
    const at = new Date(NOW.getTime() - (144 - index) * 10 * 60 * 1000);
    const hour = at.getHours() + at.getMinutes() / 60;
    const daily = Math.cos((2 * Math.PI * (hour - 20)) / 24);
    return {
      at,
      value: Math.round(820 + 600 * daily + 30 * Math.sin(index * 1.7)),
    };
  });
}

function TimeRange() {
  const [range, setRange] = useState<"24h" | "6h" | "1h">("24h");
  return (
    <Segmented
      label="Time range"
      value={range}
      onValueChange={setRange}
      options={[
        { value: "24h", label: "24 h" },
        { value: "6h", label: "6 h" },
        { value: "1h", label: "1 h" },
      ]}
    />
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-heading text-text-2">{title}</h2>
      {children}
    </section>
  );
}

function Colours() {
  const theme = (document.documentElement.dataset.theme ??
    "light") as ThemeName;
  return (
    <Card>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-3">
        {Object.entries(themes[theme].colours).map(([name, value]) => (
          <div key={name} className="flex items-center gap-2.5">
            <span
              aria-hidden="true"
              className="size-8 shrink-0 rounded-control shadow-[inset_0_0_0_1px_rgba(127,127,127,0.25)]"
              style={{ backgroundColor: value }}
            />
            <span className="flex flex-col leading-tight">
              <span className="text-small">{name}</span>
              <span className="font-mono text-meta text-text-3">{value}</span>
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}

function Type() {
  const styles: Record<keyof typeof typeScale, string> = {
    display: "text-display",
    title: "text-title",
    heading: "text-heading",
    body: "text-body",
    small: "text-small",
    meta: "text-meta",
    mono: "font-mono text-mono",
  };
  const samples: Record<keyof typeof typeScale, string> = {
    display: "1,284 players online",
    title: "Group settings",
    heading: "Scaling",
    body: "Servers start when a group's load passes its threshold, and drain when it falls below the lower one.",
    small: "Lowercase letters, digits and dashes.",
    meta: "Started 14 min ago by the autoscaler",
    mono: "8f2c1e0a9b3d4c5e 0O 1lI 5S 2Z -XX:+UseG1GC",
  };
  return (
    <Card>
      <dl className="grid grid-cols-[160px_1fr] items-baseline gap-x-6 gap-y-2.5">
        {Object.entries(samples).map(([name, sample]) => (
          <div key={name} className="contents">
            <dt className="text-meta text-text-3">{name}</dt>
            <dd className={styles[name as keyof typeof typeScale]}>{sample}</dd>
          </div>
        ))}
        <dt className="text-meta text-text-3">game</dt>
        <dd className="font-game text-[15px]">Welcome to OgWars</dd>
      </dl>
    </Card>
  );
}

function Buttons() {
  const kinds: { kind: ButtonKind; label: string }[] = [
    { kind: "primary", label: "Request server" },
    { kind: "secondary", label: "Restart" },
    { kind: "ghost", label: "Cancel" },
    { kind: "danger", label: "Kill server" },
  ];
  return (
    <Card>
      <div className="grid grid-cols-[96px_repeat(3,max-content)_24px_repeat(3,max-content)_24px_max-content] items-center gap-3">
        {kinds.map(({ kind, label }) => (
          <div key={kind} className="contents">
            <span className="font-label text-text-2">{kind}</span>
            <Button kind={kind} size="sm">
              {label}
            </Button>
            <Button kind={kind}>{label}</Button>
            <Button kind={kind} size="lg">
              {label}
            </Button>
            <span />
            {kind === "secondary" || kind === "ghost" ? (
              <>
                <Button kind={kind} size="sm" icon={RotateCcw} iconOnly>
                  Restart
                </Button>
                <Button kind={kind} icon={RotateCcw} iconOnly>
                  Restart
                </Button>
                <Button kind={kind} size="lg" icon={RotateCcw} iconOnly>
                  Restart
                </Button>
              </>
            ) : (
              <span className="col-span-3 text-meta text-text-3">
                Not icon-only
              </span>
            )}
            <span />
            <Button kind={kind} disabled>
              {label}
            </Button>
          </div>
        ))}
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-3 border-border-subtle border-t pt-4">
        <Button kind="primary" icon={Plus}>
          New group
        </Button>
        <Button icon={Upload}>Upload template</Button>
        <Button kind="primary" loading>
          Saving
        </Button>
      </div>
    </Card>
  );
}

function Fields() {
  const [routing, setRouting] = useState<
    "least" | "most" | "balanced" | "round"
  >("least");
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,420px),1fr))] gap-5">
      <Card>
        <div className="flex flex-col gap-4">
          <Field
            label="Group name"
            hint="Lowercase letters, digits and dashes."
          >
            {(control) => <Input {...control} defaultValue="bedwars" />}
          </Field>
          <Field label="Memory limit" error="Paper needs at least 1,536 MiB.">
            {(control) => (
              <Input {...control} defaultValue="1024" suffix="MiB" />
            )}
          </Field>
          <Field label="JVM flags">
            {(control) => (
              <Input
                {...control}
                mono
                defaultValue="-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
              />
            )}
          </Field>
          <div className="flex flex-col gap-1.5">
            <span className="font-label">Server id</span>
            <CopyField
              value="8f2c1e0a9b3d4c5e6f7a8b9c0d1e2f3a"
              label="server id"
            />
          </div>
        </div>
      </Card>
      <Card>
        <div className="flex flex-col gap-4">
          <Switch
            label="Maintenance"
            description="Only players with the bypass permission can join."
            defaultChecked
          />
          <Switch
            label="Tablist"
            description="Show the header and footer on every proxy."
          />
          <RadioGroup
            legend="Routing"
            value={routing}
            onValueChange={setRouting}
            options={[
              {
                value: "least",
                label: "Least filled",
                description: "Spread players across servers",
              },
              {
                value: "most",
                label: "Most filled",
                description: "Fill one server before the next",
              },
              {
                value: "balanced",
                label: "Balanced",
                description: "Least filled within the busiest half",
              },
              {
                value: "round",
                label: "Round robin",
                description: "Each server in turn",
              },
            ]}
          />
          <Checkbox label="Require a linked Minecraft account" defaultChecked />
          <TimeRange />
        </div>
      </Card>
    </div>
  );
}

function Pickers() {
  const [defaultGroup, setDefaultGroup] = useState<string>("lobby");
  const [target, setTarget] = useState<string | undefined>("bedwars");
  const [scopes, setScopes] = useState<string[]>([
    "servers:read",
    "servers:command",
    "groups:read",
  ]);
  const [expires, setExpires] = useState<Date | null>(
    new Date("2026-11-18T18:00:00"),
  );
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,420px),1fr))] gap-5">
      <Card>
        <div className="flex flex-col gap-4">
          <Field label="Default group">
            {(control) => (
              <Select
                {...control}
                options={GROUPS}
                value={defaultGroup}
                onValueChange={setDefaultGroup}
              />
            )}
          </Field>
          <Field label="Click target">
            {(control) => (
              <Combobox
                {...control}
                options={GROUPS}
                value={target}
                onValueChange={setTarget}
                placeholder="Choose a group"
              />
            )}
          </Field>
          <Field label="Scopes">
            {(control) => (
              <MultiSelect
                {...control}
                mono
                options={SCOPES}
                values={scopes}
                onValuesChange={setScopes}
                placeholder="Add scopes"
              />
            )}
          </Field>
          <Field label="Rank expires">
            {(control) => (
              <DateTimePicker
                {...control}
                value={expires}
                onChange={setExpires}
                now={NOW}
                allowPermanent
              />
            )}
          </Field>
        </div>
      </Card>
      <Card title="Colour">
        <div className="inline-block rounded-popover border border-border bg-surface shadow-popover">
          <ColourPicker
            initial="#7C5CFF"
            recent={["#7C5CFF", "#FF8800", "#FF3D7F", "#55FF55"]}
            applyLabel="Insert colour"
            onApply={() => undefined}
            onCancel={() => undefined}
          />
        </div>
      </Card>
    </div>
  );
}

function Table() {
  const toast = useToast();
  const [kill, setKill] = useState<string | null>(null);
  return (
    <Card
      flush
      title="Servers"
      actions={
        <Button kind="primary" size="sm" icon={Plus}>
          Request server
        </Button>
      }
    >
      <DataTable
        label="Servers"
        data={SERVERS}
        columns={SERVER_COLUMNS}
        getRowId={(row) => row.id}
        highlighted={(row) => row.id === "bedwars-5c1e09"}
        empty={null}
        actions={(row) => ({
          inline: [
            {
              label: `Stop ${row.id}`,
              icon: Square,
              onPress: () => toast({ message: `Stopping ${row.id}` }),
            },
            {
              label: `Push the template of ${row.id}`,
              icon: Upload,
              onPress: () => toast({ message: `Pushing ${row.id}` }),
            },
          ],
          menu: [
            [
              {
                label: "Stop",
                icon: Square,
                onSelect: () => toast({ message: `Stopping ${row.id}` }),
              },
              {
                label: "Push template",
                icon: Upload,
                onSelect: () => toast({ message: `Pushing ${row.id}` }),
              },
            ],
            [
              {
                label: "Kill",
                icon: Skull,
                danger: true,
                onSelect: () => setKill(row.id),
              },
            ],
          ],
        })}
      />
      <ConfirmDialog
        open={kill !== null}
        onOpenChange={(open) => (open ? undefined : setKill(null))}
        title={`Kill ${kill ?? ""}?`}
        description="The pod is deleted at once and its players are disconnected. This cannot be undone."
        confirmLabel="Kill server"
        typedName={kill ?? ""}
        onConfirm={() => undefined}
      />
    </Card>
  );
}

/** A list whose rows did not load: the reason in place of the rows, and a retry. */
function FailedTable() {
  return (
    <Card flush title="Servers that did not load">
      <DataTable
        label="Servers that did not load"
        data={[]}
        columns={SERVER_COLUMNS}
        getRowId={(row) => row.id}
        empty={null}
        failure={{
          message:
            "GET /api/v1/players/online answered 503: Valkey is unavailable",
          retry: () => undefined,
        }}
      />
    </Card>
  );
}

function Feedback() {
  const toast = useToast();
  const [stopOpen, setStopOpen] = useState(false);
  const [changes, setChanges] = useState(2);
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,420px),1fr))] gap-5">
      <Card>
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => setStopOpen(true)}>Open a dialog</Button>
            <Button
              onClick={() =>
                toast({
                  message: "Deleted skywars. You can restore it for 7 days.",
                  action: { label: "Undo", onPress: () => undefined },
                })
              }
            >
              Show a toast
            </Button>
          </div>
          <Note tone="warning" icon={TriangleAlert}>
            <span className="font-label">smp is running.</span> A template you
            activate now is replaced by the server's own files when it stops.
            Stop it first to keep your change.
          </Note>
          <div className="flex flex-wrap gap-2">
            <Badge tone="neutral">Requested</Badge>
            <Badge tone="info" icon={LoaderCircle}>
              Starting
            </Badge>
            <Badge tone="success" icon={CircleCheck}>
              Running
            </Badge>
            <Badge tone="warning" icon={CirclePause}>
              Draining
            </Badge>
            <Badge tone="neutral">Stopped</Badge>
            <Badge tone="danger" icon={TriangleAlert}>
              Paused
            </Badge>
            <Badge tone="warning" icon={CirclePause}>
              Maintenance
            </Badge>
          </div>
          <SaveBar
            changes={changes}
            onDiscard={() => setChanges(0)}
            onSave={() => setChanges(0)}
          />
        </div>
      </Card>
      <EmptyState
        icon={Plus}
        title="No NPCs in lobby yet"
        description="An NPC stands in the world and moves players to a group when they click it."
        action={
          <Button kind="primary" icon={Plus}>
            Add NPC
          </Button>
        }
      />
      <Dialog
        open={stopOpen}
        onOpenChange={setStopOpen}
        title="Stop lobby-3f2a9c?"
        description="Its 82 players move to the default group, then the server stops."
        footer={
          <>
            <Button onClick={() => setStopOpen(false)}>Cancel</Button>
            <Button kind="primary" onClick={() => setStopOpen(false)}>
              Stop server
            </Button>
          </>
        }
      />
    </div>
  );
}

function Charts() {
  const time = new Intl.DateTimeFormat("en", {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });
  const number = new Intl.NumberFormat("en");
  return (
    <div className="grid grid-cols-[minmax(0,2fr)_minmax(300px,1fr)] gap-5">
      <Card title="Players online" actions={<TimeRange />}>
        <div className="mb-1 flex items-baseline gap-2.5">
          <span className="text-display tabular-nums">1,284</span>
          <span className="text-text-2">of 5,000 slots</span>
        </div>
        <TimeSeriesChart
          label="Players online over the last 24 hours"
          series={[{ name: "Players", points: playersSeries() }]}
          height={170}
          formatValue={(value) => number.format(value)}
          formatTime={(at) => time.format(at)}
        />
      </Card>
      <Card title="Servers">
        <StackedBar
          segments={[
            { label: "Running", value: 23, tone: "success" },
            { label: "Starting", value: 1, tone: "info" },
            { label: "Draining", value: 1, tone: "warning" },
          ]}
        />
        <ul className="mt-3 flex flex-col gap-1.5">
          <li className="flex items-center gap-2">
            <CircleCheck
              aria-hidden="true"
              size={16}
              className="text-success-text"
            />
            <span className="flex-1">Running</span>
            <span className="font-label tabular-nums">23</span>
          </li>
          <li className="flex items-center gap-2">
            <LoaderCircle
              aria-hidden="true"
              size={16}
              className="text-info-text"
            />
            <span className="flex-1">Starting</span>
            <span className="font-label tabular-nums">1</span>
          </li>
          <li className="flex items-center gap-2">
            <CirclePause
              aria-hidden="true"
              size={16}
              className="text-warning-text"
            />
            <span className="flex-1">Draining</span>
            <span className="font-label tabular-nums">1</span>
          </li>
        </ul>
      </Card>
    </div>
  );
}

function Game() {
  const [source, setSource] = useState(
    "<gradient:#ff8800:#ff3d7f>OgWars</gradient> <grean>Bedwars, SkyWars and more",
  );
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,520px),1fr))] gap-5">
      <Card title="MOTD">
        <Field
          label="First line"
          error="Unknown tag <grean>. Did you mean <green>?"
        >
          {(control) => (
            <TagTextField
              {...control}
              value={source}
              onChange={setSource}
              problems={[{ offset: 44, length: 7 }]}
              placeholders={[]}
              recentColours={["#FF8800", "#FF3D7F"]}
            />
          )}
        </Field>
        <div className="mt-4 flex flex-col gap-3">
          <PreviewFrame label="1.16 and newer">
            <ServerListPreview
              icon={ICON}
              name="OgWars Network"
              motd={motd(false)}
              players="1284/5000"
            />
          </PreviewFrame>
          <PreviewFrame label="1.8 to 1.15">
            <ServerListPreview
              icon={ICON}
              name="OgWars Network"
              motd={motd(true)}
              players="1284/5000"
            />
          </PreviewFrame>
        </div>
      </Card>
      <Card title="Chat, tablist and NPCs">
        <div className="flex flex-col gap-3">
          <PreviewFrame label="Chat">
            <ChatPreview
              lines={[
                [
                  ...gradient("[Admin]", "#FF5555", "#FFAA00", false),
                  { text: " mira" },
                  {
                    text: ": welcome to the new lobby",
                    color: minecraftColours.gray,
                  },
                ],
                [
                  { text: "[VIP]", color: minecraftColours.green },
                  { text: " kiwibear" },
                  { text: ": gg", color: minecraftColours.gray },
                ],
              ]}
            />
          </PreviewFrame>
          <PreviewFrame label="Tablist">
            <TablistPreview
              header={gradient(
                "OgWars Network",
                "#FF8800",
                "#FF3D7F",
                false,
                true,
              )}
              entries={[
                [
                  ...gradient("Admin ", "#FF5555", "#FFAA00", false),
                  { text: "mira" },
                ],
                [
                  { text: "VIP ", color: minecraftColours.green },
                  { text: "kiwibear" },
                ],
                [{ text: "Tobbe04", color: minecraftColours.gray }],
                [{ text: "redstoneRita", color: minecraftColours.gray }],
              ]}
              footer={[
                { text: "1,284", color: minecraftColours.yellow },
                { text: " players online", color: minecraftColours.gray },
              ]}
            />
          </PreviewFrame>
          <PreviewFrame label="NPC title">
            <NametagPreview
              lines={[
                [{ text: "Bedwars", color: minecraftColours.gold, bold: true }],
                [
                  { text: "828", color: minecraftColours.yellow },
                  { text: " playing", color: minecraftColours.gray },
                ],
              ]}
            />
          </PreviewFrame>
        </div>
      </Card>
    </div>
  );
}

/** Every component in the kit, for the screenshot run and for review in a browser. */
export function Sheet() {
  return (
    <main className="mx-auto flex flex-col gap-8 p-8">
      <PageHeader
        title="OgCloud UI kit"
        description="The Layered direction (D-0216) in every component, as the dashboard uses them."
        actions={
          <>
            <Button>Secondary action</Button>
            <Button kind="primary" icon={Plus}>
              Primary action
            </Button>
          </>
        }
      />
      <Section title="Colour">
        <Colours />
      </Section>
      <Section title="Type">
        <Type />
      </Section>
      <Section title="Buttons">
        <Buttons />
      </Section>
      <Section title="Fields and choices">
        <Fields />
      </Section>
      <Section title="Pickers">
        <Pickers />
      </Section>
      <Section title="Table">
        <Table />
        <FailedTable />
      </Section>
      <Section title="Charts">
        <Charts />
      </Section>
      <Section title="Feedback">
        <Feedback />
      </Section>
      <Section title="In-game texts">
        <Game />
      </Section>
    </main>
  );
}
