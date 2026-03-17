#!/usr/bin/env node

import { closeCli, runCli } from "./app";
import { fail } from "./cli/output";

runCli(process.argv.slice(2))
    .catch((error) => {
        const message = error instanceof Error ? error.message : String(error);
        fail(message);
        process.exitCode = 1;
    })
    .finally(() => {
        closeCli();
    });
