import { info } from "../cli/output";
import { runCommand } from "./process";

export function helmUpgradeInstall(
    releaseName: string,
    chartPath: string,
    namespace: string,
    valuesFilePath: string,
    createNamespace: boolean,
): void {
    const args = [
        "upgrade",
        "--install",
        releaseName,
        chartPath,
        "--namespace",
        namespace,
        "-f",
        valuesFilePath,
        "--wait",
        "--timeout",
        "15m",
    ];

    if (createNamespace) {
        args.push("--create-namespace");
    }

    info(`helm ${args.join(" ")}`);
    runCommand("helm", args, { stdio: "inherit" });
}

export function helmUninstall(releaseName: string, namespace: string): void {
    info(`Uninstalling ${releaseName}...`);
    runCommand("helm", ["uninstall", releaseName, "-n", namespace], {
        allowFailure: true,
        stdio: "inherit",
    });
}
