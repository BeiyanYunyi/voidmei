{
  description = "VoidMei Kotlin Multiplatform desktop application";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };
  };

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

  outputs = inputs@{ flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" ];
      imports = [ ./nix/flake-module.nix ];
    };
}
