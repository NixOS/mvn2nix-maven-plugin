mvn2nix
========

**Archived**: see <https://github.com/fzakaria/mvn2nix>

![master](https://github.com/NixOS/mvn2nix-maven-plugin/actions/workflows/main.yml/badge.svg)

Generate `project-info.json` for use with nix's Maven repository generation
functions.

Note: This is slower than anyone would like and not expected to be perfect.
Please open issues if something isn't working for you!

Appears to require maven 3.2.5 to build locally.

Usage
------

`mvn org.nixos.mvn2nix:mvn2nix-maven-plugin:mvn2nix`

System properties
------------------

* `mvn2nixOutputFile`: Change the name of the file generated by mvn2nix
