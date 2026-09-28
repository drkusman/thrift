// Bypasses bubblewrap's interactive `init` wizard (incompatible with non-TTY stdin) by
// building the TwaManifest and Android project programmatically via @bubblewrap/core directly.
const path = require("path");
const fs = require("fs");
const { TwaManifest, TwaGenerator, ConsoleLog } = require(
  "C:/Users/Hp/AppData/Roaming/npm/node_modules/@bubblewrap/cli/node_modules/@bubblewrap/core"
);

const MANIFEST_URL = "http://16.16.207.178/manifest.webmanifest";
const TARGET_DIR = __dirname;
const MANIFEST_FILE = path.join(TARGET_DIR, "twa-manifest.json");

async function main() {
  const twaManifest = await TwaManifest.fromWebManifest(MANIFEST_URL);

  twaManifest.packageId = "ng.asuu.thrift.twa";
  twaManifest.name = "ASUU-MOAUM Thrift & Savings";
  twaManifest.launcherName = "ASUU Thrift";
  twaManifest.signingKey = {
    path: path.join(TARGET_DIR, "android.keystore"),
    alias: "asuu-thrift",
  };
  twaManifest.appVersionCode = 1;
  twaManifest.appVersionName = "1.0.0";

  const err = twaManifest.validate();
  if (err) {
    throw new Error("Invalid TWA manifest: " + err);
  }

  await twaManifest.saveToFile(MANIFEST_FILE);
  console.log("Wrote", MANIFEST_FILE);

  const twaGenerator = new TwaGenerator();
  await twaGenerator.removeTwaProject(TARGET_DIR);
  await twaGenerator.createTwaProject(TARGET_DIR, twaManifest, new ConsoleLog("gen"), () => {});

  const manifestContents = fs.readFileSync(MANIFEST_FILE);
  const sum = require("crypto").createHash("sha1").update(manifestContents).digest("hex");
  fs.writeFileSync(path.join(TARGET_DIR, "manifest-checksum.txt"), sum);

  // Bubblewrap's generated build.gradle always launches over https, since TWAs normally require
  // it for Digital Asset Link verification - but production has no TLS yet, so patch it to http
  // or the app fails to load. Revert this once the site has a real domain + HTTPS.
  const gradleFile = path.join(TARGET_DIR, "app", "build.gradle");
  const gradleContents = fs.readFileSync(gradleFile, "utf8");
  const patched = gradleContents.replace(
    'def launchUrl = "https://" + twaManifest.hostName + twaManifest.launchUrl',
    'def launchUrl = "http://" + twaManifest.hostName + twaManifest.launchUrl'
  );
  if (patched === gradleContents) {
    throw new Error("Expected https:// launchUrl line not found in build.gradle - template may have changed");
  }
  fs.writeFileSync(gradleFile, patched);
  console.log("Patched build.gradle launchUrl to http://");

  console.log("Android project generated at", TARGET_DIR);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
