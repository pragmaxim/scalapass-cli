## ZIO pass

This is a password-store implementation demonstrating zio-cli in action. It can be initialized with different backends
for encryption/decryption/signing (BouncyCastle, GnuPG, PgPainless) and version control (Git, JGit).

### Build

For building, [zio-cli-installer](https://github.com/zio/zio-cli/blob/master/installer.sh) is used to create 
a very fast GraalVM based native image and a completion-script.

```bash
./install.sh
cd bin
export PATH=.:$PATH

zio-pass
```

```
COMMANDS

  - init [--debug] [--pgp-type bc | gnupg | pgpainless] [--git-type git | jgit] <gpg-id>  Initialize store with git and pgp implementation types
  - insert [--debug] [-f] <pass-name>                                                     Insert password in a folder format, eg. web/google.com/foo@gmail.com
  - show [--debug] <pass-name>                                                            Show password
  - cp [--debug] <pass-name>                                                              Copy password
  - ls [--debug] <folder>                                                                 List passwords of given folder
  - tree [--debug]                                                                        List passwords
```

