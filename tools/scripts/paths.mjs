import {fileURLToPath} from "node:url";

export const repositoryRoot = fileURLToPath(new URL("../../", import.meta.url));
export const toolsRoot = fileURLToPath(new URL("../", import.meta.url));
