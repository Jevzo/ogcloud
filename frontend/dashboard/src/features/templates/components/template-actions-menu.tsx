import { FileArchiveIcon, LoaderCircleIcon, MoreHorizontalIcon, Trash2Icon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { TemplateRecord } from "@/types/template";

interface TemplateActionsMenuProps {
    activeActionKey: string | null;
    align?: "center" | "end" | "start";
    onDeleteTemplate: (template: TemplateRecord) => void;
    onDownloadTemplate: (template: TemplateRecord) => void;
    template: TemplateRecord;
}

const TemplateActionsMenu = ({
    activeActionKey,
    align = "end",
    onDeleteTemplate,
    onDownloadTemplate,
    template,
}: TemplateActionsMenuProps) => {
    const templateActionPrefix = `${template.group}:${template.version}`;
    const isActionInProgress = activeActionKey !== null;
    const isTemplateBusy = activeActionKey?.startsWith(templateActionPrefix) ?? false;

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Open actions for ${template.group} ${template.version}`}
                    disabled={isActionInProgress}
                >
                    {isTemplateBusy ? (
                        <LoaderCircleIcon className="size-4 animate-spin" />
                    ) : (
                        <MoreHorizontalIcon />
                    )}
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align={align} className="w-56 min-w-56">
                <DropdownMenuLabel>Template actions</DropdownMenuLabel>
                <DropdownMenuItem
                    disabled={isActionInProgress}
                    onSelect={(event) => {
                        event.preventDefault();
                        onDownloadTemplate(template);
                    }}
                >
                    <FileArchiveIcon />
                    Download archive
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                    variant="destructive"
                    disabled={isActionInProgress}
                    onSelect={(event) => {
                        event.preventDefault();
                        onDeleteTemplate(template);
                    }}
                >
                    <Trash2Icon />
                    Delete template
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
};

export default TemplateActionsMenu;
