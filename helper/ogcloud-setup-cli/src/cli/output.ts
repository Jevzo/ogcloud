import { COLORS } from "../shared/constants";

export function color(text: string, ...codes: string[]): string {
    return `${codes.join("")}${text}${COLORS.reset}`;
}

export function heading(message: string): void {
    console.log(color(message, COLORS.bold, COLORS.cyan));
}

export function info(message: string): void {
    console.log(`${color("INFO", COLORS.bold, COLORS.cyan)} ${message}`);
}

export function warn(message: string): void {
    console.log(`${color("WARN", COLORS.bold, COLORS.yellow)} ${message}`);
}

export function ok(message: string): void {
    console.log(`${color("OK", COLORS.bold, COLORS.green)} ${message}`);
}

export function fail(message: string): void {
    console.error(`${color("ERROR", COLORS.bold, COLORS.red)} ${message}`);
}
