# SDLPPX Packager

This utility is used to create Trados Return Package files (`SDLRPX`) from a `SDLPPX` file. Of course, it assumes you already have translated the content of the `SDLXLIFF` files inside.

It can also extract the source files, translation memories (`SDLTM`) and glossaries (`SDLTB`) located inside a `SDLPPX` project and copy them to a `source/` directory (in an OmegaT project, for instance).

It can work both in command line or with the GUI. When using the CLI, you can use a sdlppx, sdltm or sdltb file as the source file.

![alt text](screenshot.png "SDLPPX Packager Screenshot")

## How does this work?

1. We make a backup copy of the orginal `sdlppx` file.
2. If the PackageType attribute in the `sdlproj` file at the root of the `sdlppx` is `ProjectPackage`, we changes this attribute to `Return Package`. Otherwise, we don't do anything.
3. The target language is determined by looking at the attribute `/PackageProject/LanguageDirections/LanguageDirection/@TargetLanguageCode` in the  `sdlproj`.
4. For each `.sdlxliff` file in the target language directory of the  `sdlppx`, we replace it with the corresponding translated file from the target directory.
5. The `sdlppx` file is renamed with a `.sdlrpx` extension.

## Installation

* To use the GUI: `bin/SDLPPXPackager`
* To use in CLI: `bin/SDLPPXPackager --project-dir /path/to/project/ /path/to/project.sdl[ppx|tm|tb]`

```shell
usage: SDLPPXPackager [options] --project-dir project_dir sdlppx
 -p,--project-dir <arg>   project directory
 -r,--return              create the return SDLPRX package (default)
 -e,--extract             extract source files from the SDLPPX

 -ng,--no-glossary        skip the SDLTB glossary extraction
 -ns,--no-source          skip the SDLXLIFF sources extraction
 -nt,--no-tm              skip the SDLTM memory extraction

 -G,--gui                 force the GUI mode
 -h,--help                print this message and exit
```

## See Also

This utility was made after watching this video "[Handle SDL Trados Studio Packages without using SDL Trados Studio](https://www.youtube.com/watch?v=a4ZGeAjTl2M)", made by Fi2Pro.

The Glossary and TM extraction is mostly copied from [Trados-Studio-Resource-Converter](https://github.com/TomasoAlbinoni/Trados-Studio-Resource-Converter).

## License

This project is distributed under the GNU general public license version 3 or later.
