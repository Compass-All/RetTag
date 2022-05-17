# RetTag: Hardware-assisted Return Address Integrity on RISC-V

## Overview

In this document, we provide a guideline for developing our RetTag [^1]. We run our experiments on the Xilinx Kintex-7 FPGA KC705 evaluation board.

As a first step, clone this repository:
```
$ git clone https://github.com/Compass-All/RetTag
```
## Contents

- `patches/gcc.patch`: patch for RISC-V GNU Compiler Toolchain [^2] to recognize and emit custom instructions.
- `patches/kernel.patch`: patch for Linux Kernel [^3] to handle RoCC interrupt when running on the FPGA.
- `patches/llvm.patch`: patch for LLVM Compiler Infrastructure [^4] to recognize and emit custom instructions.
- `patches/pk.patch`: patch for RISC-V Proxy Kernel and Boot Loader [^5] to support custom instructions and RoCC interrupt and handle RoCC interrupt when running on the emulator.
- `rocket-chip/`: part of the source code of Rocket Chip Generator [^6] to implement the hardware extension of RetTag.

## Usage

1. Apply the gcc.patch and llvm.patch to build your compiler and get  `riscv64-unknown-elf-gcc` or `riscv64-unknown-linux-gnu-gcc` and `clang`.

2. Apply the directory rocket-chip/ to build your rocket core.

   The directory rocket-chip/bootrom contains the configuration for Xilinx Kintex-7 FPGA KC705 and emulator, correspondingly.

   To build the C simulator:
   ```
   $ cd bootrom
   $ cp -r verilater/* ./
   $ make
   $ cd ../emulator
   $ make
   ```
   and get `emulator-freechips.rocketchip.system-freechips.rocketchip.system.DefaultConfig`

   Or to build the bitstream:
   ```
   $ cd bootrom
   $ cp -r fpga/* ./
   $ make
   $ cd ../emulator
   $ make
   ```
   and boot linux on a Rocket-chip SoC, please refer to [1](https://github.com/TwistsOfFate/fpga-rocket-chip/tree/kc705).

3. Apply the linux.patch to build your modified linux kernel.

4. Apply the pk.patch to build your project and get `pk`.

5. Run a test file.

   To compile a test file:
   ```
   $ riscv64-unknown-elf-gcc -static hello.c -o hello
   ```
   or
   ```
   $ clang  --gcc-toolchain='path_to_riscv-gcc/' --sysroot='path_to_riscv-gcc/sysroot' -static hello.c -o hello
   ```

   To run on the emulator and get the run log:
   ```
   $ ./emulator-freechips.rocketchip.system-freechips.rocketchip.system.DefaultConfig +verbose pk hello > run.log
   ```
For the complete llvm project, please see [2](https://gitee.com/stwjt/llvm-project).

## Additional Documentation

### Citation

If you use this repository for research, please cite our paper:

```
@inproceedings{wang2022rettag,
  title={RetTag: hardware-assisted return address integrity on RISC-V},
  author={Wang, Yu and Wu, Jinting and Yue, Tai and Ning, Zhenyu and Zhang, Fengwei},
  booktitle={Proceedings of the 15th European Workshop on Systems Security},
  pages={50--56},
  year={2022}
}
```
### Publication

Wang, Yu, et al. "RetTag: hardware-assisted return address integrity on RISC-V." *Proceedings of the 15th European Workshop on Systems Security*. 2022.

  * [Paper](https://dl.acm.org/doi/pdf/10.1145/3517208.3523758)

### Reference

[^1]: Wang, Yu, et al. "RetTag: hardware-assisted return address integrity on RISC-V." *Proceedings of the 15th European Workshop on Systems Security*. 2022.
[^2]: RISC-V GNU Compiler Toolchain. https://github.com/riscv-collab/riscv-gnu-toolchain
[^3]: Linux Kernel. https://github.com/torvalds/linux
[^4]: LLVM Compiler Infrastructure. https://github.com/llvm/llvm-project
[^5]: RISC-V Proxy Kernel and Boot Loader. https://github.com/riscv-software-src/riscv-pk
[^6]: Rocket Chip Generator. https://github.com/chipsalliance/rocket-chip
