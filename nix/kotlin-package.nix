{ lib, stdenvNoCC, gradle, jdk21, nodejs, makeWrapper, binutils, glibcLocales, makeDesktopItem
, libx11, libxext, libxrender, libxi, libxtst, libxrandr
, libxkbcommon, libxcb, libxt, libxinerama, libGL, fontconfig, freetype
}:
let
  desktopItem = makeDesktopItem {
    name = "voidmei-kotlin";
    desktopName = "VoidMei Kotlin";
    comment = "War Thunder telemetry and HUD";
    exec = "voidmei-kotlin";
    icon = "voidmei-kotlin";
    terminal = false;
    categories = [ "Game" ];
    actions = {
      recover = { name = "不打开 HUD（恢复）"; exec = "voidmei-kotlin --no-hud"; };
      compatible = { name = "使用兼容显示打开 HUD"; exec = "voidmei-kotlin --hud --compatible-hud"; };
    };
  };
in
stdenvNoCC.mkDerivation (finalAttrs: {
  pname = "voidmei-kotlin";
  version = "2.0.0";
  src = lib.fileset.toSource {
    root = ../.;
    fileset = lib.fileset.unions [
      ../build.gradle.kts ../settings.gradle.kts ../gradle.properties
      ../core/build.gradle.kts ../core/src
      ../desktop/build.gradle.kts ../desktop/src ../voice
      ../script/mock_data.json ../script/mock_scenarios/snapshots
      ../ui_layout.cfg
      ../image/16x16.png
    ];
  };
  nativeBuildInputs = [ gradle jdk21 nodejs makeWrapper binutils ];
  JAVA_HOME = jdk21;
  LANG = "en_US.UTF-8";
  LC_ALL = "en_US.UTF-8";
  LOCALE_ARCHIVE = "${glibcLocales}/lib/locale/locale-archive";
  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./kotlin-deps.json;
  };
  gradleFlags = [ "-Pvoidmei.systemNode=true" ];
  gradleBuildTask = ":desktop:createDistributable";
  gradleCheckTask = ":core:jvmTest :desktop:test";
  gradleUpdateTask = ":desktop:createDistributable :core:jvmTest :desktop:test";
  doCheck = true;
  installPhase = ''
    runHook preInstall
    mkdir -p "$out/lib" "$out/bin"
    cp -r desktop/build/compose/binaries/main/app/VoidMei "$out/lib/voidmei"
    makeWrapper "$out/lib/voidmei/bin/VoidMei" "$out/bin/voidmei-kotlin" \
      --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath [
        libx11 libxext libxrender libxi libxtst libxrandr libxkbcommon
        libxcb libxt libxinerama libGL fontconfig freetype
      ]}
    mkdir -p "$out/share/applications" "$out/share/icons/hicolor/16x16/apps"
    cp ${desktopItem}/share/applications/voidmei-kotlin.desktop "$out/share/applications/"
    cp image/16x16.png "$out/share/icons/hicolor/16x16/apps/voidmei-kotlin.png"
    substituteInPlace "$out/share/applications/voidmei-kotlin.desktop" \
      --replace-fail 'Exec=voidmei-kotlin' "Exec=$out/bin/voidmei-kotlin"
    runHook postInstall
  '';
  meta = {
    description = "VoidMei Kotlin desktop application";
    license = lib.licenses.gpl3Only;
    platforms = [ "x86_64-linux" ];
    mainProgram = "voidmei-kotlin";
  };
})
