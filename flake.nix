{
  description = "VoidMei Java 8 desktop application";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  nixConfig = {
    experimental-features = "nix-command flakes";
    extra-substituters = [
      # "https://mirror.nju.edu.cn/nix-channels/store?priority=9"
      "https://mirror.sjtu.edu.cn/nix-channels/store?priority=10"
      "https://cache.nixos-cuda.org"
    ];
    extra-trusted-public-keys = [
      "cache.nixos-cuda.org:74DUi4Ye579gUqzH4ziL9IyiJBlDpMRn9MBN8oNan9M="
    ];

    # for the first time setup, you may need to set up access token
    # access-tokens = "github.com=${secrets.github-token}";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in {
      packages = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; };
          nativeLibraries = with pkgs; [
            libx11 libxt libxtst libxext libxrender libxi libxrandr
            libxkbcommon libxinerama libxcursor
          ];
        in {
          default = self.packages.${system}.voidmei;
          voidmei = pkgs.stdenvNoCC.mkDerivation {
            pname = "voidmei";
            version = "unstable";
            src = pkgs.lib.cleanSourceWith {
              src = ./.;
              filter = path: type:
                let name = builtins.baseNameOf path;
                in builtins.elem name [ "src" "dep" "fonts" "image" "lang" "voice" "test" "script" ]
                  || builtins.elem name [ "MANIFEST.MF" "ui_layout.cfg" ]
                  || builtins.any (dir: pkgs.lib.hasPrefix (toString ./. + "/" + dir + "/") path)
                    [ "src" "dep" "fonts" "image" "lang" "voice" "test" "script" ];
            };
            nativeBuildInputs = [ pkgs.jdk8 pkgs.python3 pkgs.makeWrapper ];
            passthru.libraryPath = pkgs.lib.makeLibraryPath nativeLibraries;
            buildPhase = ''
              runHook preBuild
              python script/build.py compile
              python script/build.py jar
              runHook postBuild
            '';
            doCheck = true;
            checkPhase = ''
              runHook preCheck
              python script/build.py test
              runHook postCheck
            '';
            installPhase = ''
              runHook preInstall
              mkdir -p "$out/share/voidmei" "$out/bin"
              cp -r VoidMei.jar dep fonts image lang voice ui_layout.cfg "$out/share/voidmei/"
              # JNativeHook otherwise extracts beside its JAR at runtime, which
              # fails in the read-only store. Supply the file it looks for.
              jar xf dep/jnativehook-2.2.2.jar com/github/kwhat/jnativehook/lib/linux/x86_64/libJNativeHook.so
              cp com/github/kwhat/jnativehook/lib/linux/x86_64/libJNativeHook.so \
                "$out/share/voidmei/dep/libJNativeHook-2.2.2.x86_64.so"
              makeWrapper ${pkgs.bash}/bin/bash "$out/bin/voidmei" \
                --add-flags "$out/share/voidmei/launch.sh" \
                --prefix LD_LIBRARY_PATH : ${pkgs.lib.makeLibraryPath nativeLibraries} \
                --prefix PATH : ${pkgs.lib.makeBinPath [ pkgs.coreutils ]}
              cp ${./nix/launch.sh} "$out/share/voidmei/launch.sh"
              substituteInPlace "$out/share/voidmei/launch.sh" \
                --replace-fail '@assets@' "$out/share/voidmei" \
                --replace-fail '@java@' '${pkgs.jdk8}/bin/java'
              runHook postInstall
            '';
            meta = {
              description = "War Thunder telemetry desktop frontend";
              license = pkgs.lib.licenses.gpl3Only;
              platforms = systems;
              mainProgram = "voidmei";
            };
          };
        });
      apps = forAllSystems (system: {
        default = {
          type = "app";
          program = "${self.packages.${system}.voidmei}/bin/voidmei";
          meta.description = "Launch VoidMei";
        };
      });
      devShells = forAllSystems (system:
        let pkgs = import nixpkgs { inherit system; };
        in { default = pkgs.mkShell {
          inputsFrom = [ self.packages.${system}.voidmei ];
          LD_LIBRARY_PATH = self.packages.${system}.voidmei.libraryPath;
        }; });
    };
}
