DESCRIPTION = "ARM Trusted Firmware"

LICENSE = "BSD"
LIC_FILES_CHKSUM = "file://license.rst;md5=1dd070c98a281d18d9eefd938729b031"

inherit deploy

DEPENDS += "u-boot-mkimage-native u-boot openssl openssl-native mbedtls rcw cst-native bc-native"
DEPENDS_append_lx2160a += "ddr-phy"
do_compile[depends] += "u-boot:do_deploy rcw:do_deploy uefi:do_deploy"

PV_append = "+${SRCPV}"

ATF_BRANCH ?= "lf_v2.4"
ATF_SRC ?= "git://bitbucket.sw.nxp.com/lfac/atf-nxp.git;protocol=ssh"
SRC_URI = "${ATF_SRC};branch=${ATF_BRANCH} \
    git://github.com/ARMmbed/mbedtls;nobranch=1;destsuffix=git/mbedtls;name=mbedtls \
"
SRCREV = "${AUTOREV}"
SRCREV_mbedtls = "0795874acdf887290b2571b193cafd3c4041a708"
SRCREV_FORMAT = "atf"

S = "${WORKDIR}/git"

COMPATIBLE_MACHINE = "(qoriq)"

PACKAGE_ARCH = "${MACHINE_ARCH}"

PLATFORM = "${MACHINE}"
PLATFORM_ls1088ardb-pb = "ls1088ardb"
PLATFORM_lx2160ardb-rev2 = "lx2160ardb"
PLATFORM_ADDITIONAL_TARGET ??= ""
PLATFORM_ADDITIONAL_TARGET_ls1012afrwy = "ls1012afrwy_512mb"

RCW_FOLDER ?= "${MACHINE}"
RCW_FOLDER_ls1088ardb-pb = "ls1088ardb"

RCW_SUFFIX ?= ".bin"
RCW_SUFFIX_ls1012a = "${@bb.utils.contains('DISTRO_FEATURES', 'secure', '_sben.bin', '_default.bin', d)}"
RCW_SUFFIX_ls1043a = "${@bb.utils.contains('DISTRO_FEATURES', 'secure', '_sben.bin', '.bin', d)}"
RCW_SUFFIX_ls1046a = "${@bb.utils.contains('DISTRO_FEATURES', 'secure', '_sben.bin', '.bin', d)}"

# requires CROSS_COMPILE set by hand as there is no configure script
export CROSS_COMPILE="${TARGET_PREFIX}"
export ARCH="arm64"

# Let the Makefile handle setting up the CFLAGS and LDFLAGS as it is
# a standalone application
CFLAGS[unexport] = "1"
LDFLAGS[unexport] = "1"
AS[unexport] = "1"
LD[unexport] = "1"

EXTRA_OEMAKE += "HOSTCC='${BUILD_CC} ${BUILD_CPPFLAGS} ${BUILD_CFLAGS} ${BUILD_LDFLAGS}'"
EXTRA_OEMAKE += "\
    ${@bb.utils.contains('COMBINED_FEATURES', 'optee', 'BL32=${RECIPE_SYSROOT}${nonarch_base_libdir}/firmware/tee_${MACHINE}.bin SPD=opteed', '', d)} \
"

BOOTTYPE ?= "nor nand qspi flexspi_nor sd emmc"
BUILD_SECURE = "${@bb.utils.contains('DISTRO_FEATURES', 'secure', 'true', 'false', d)}"
BUILD_FUSE = "${@bb.utils.contains('DISTRO_FEATURES', 'fuse', 'true', 'false', d)}"

PACKAGECONFIG ??= " \
    ${@bb.utils.filter('COMBINED_FEATURES', 'optee', d)} \
"
PACKAGECONFIG[optee] = ",,optee-os-qoriq"

uboot_boot_sec ?= "${DEPLOY_DIR_IMAGE}/u-boot.bin-tfa-secure-boot"
uboot_boot ?= "${DEPLOY_DIR_IMAGE}/u-boot.bin-tfa"

chassistype ?= "ls2088_1088"
chassistype_ls1012ardb = "ls104x_1012"
chassistype_ls1012afrwy = "ls104x_1012"
chassistype_ls1043ardb = "ls104x_1012"
chassistype_ls1046ardb = "ls104x_1012"
chassistype_ls1046afrwy = "ls104x_1012"

ddrphyopt ?= ""
ddrphyopt_lx2160ardb = "fip_ddr_sec"
ddrphyopt_lx2160ardb-rev2 = "fip_ddr_sec"

do_configure[noexec] = "1"

do_compile() {
    export LIBPATH="${RECIPE_SYSROOT_NATIVE}"
    install -d ${S}/include/tools_share/openssl
    cp -r ${RECIPE_SYSROOT}/usr/include/openssl/* ${S}/include/tools_share/openssl
    if [ ! -f ${RECIPE_SYSROOT_NATIVE}/usr/bin/cst/srk.pri ]; then
       ${RECIPE_SYSROOT_NATIVE}/usr/bin/cst/gen_keys 1024
    else
       cp ${RECIPE_SYSROOT_NATIVE}/usr/bin/cst/srk.pri ${S}
       cp ${RECIPE_SYSROOT_NATIVE}/usr/bin/cst/srk.pub ${S}
    fi

    if [ "${BUILD_FUSE}" = "true" ]; then
       ${RECIPE_SYSROOT_NATIVE}/usr/bin/cst/gen_fusescr ${RECIPE_SYSROOT_NATIVE}/usr/bin/cst/input_files/gen_fusescr/${chassistype}/input_fuse_file
       fuseopt="fip_fuse FUSE_PROG=1 FUSE_PROV_FILE=fuse_scr.bin"
    fi
    if [ "${BUILD_SECURE}" = "true" ]; then
        secureopt="TRUSTED_BOARD_BOOT=1 ${ddrphyopt} CST_DIR=${RECIPE_SYSROOT_NATIVE}/usr/bin/cst"
        secext="_sec"
        bl33="${uboot_boot_sec}"
    else
        bl33="${uboot_boot}"
    fi       

    if [ -f ${DEPLOY_DIR_IMAGE}/ddr-phy/ddr4_pmu_train_dmem.bin ]; then
        cp ${DEPLOY_DIR_IMAGE}/ddr-phy/*.bin ${S}/
    fi

    for d in ${BOOTTYPE}; do
        case $d in
        nor)
            rcwimg="${RCWNOR}${RCW_SUFFIX}"
            uefiboot="${UEFI_NORBOOT}"
            ;;
        nand)
            rcwimg="${RCWNAND}${RCW_SUFFIX}"
            ;;
        qspi)
            rcwimg="${RCWQSPI}${RCW_SUFFIX}"
            uefiboot="${UEFI_QSPIBOOT}"
            if [ "${BUILD_SECURE}" = "true" ] && [ ${MACHINE} = ls1046ardb ]; then
                rcwimg="RR_FFSSPPPH_1133_5559/rcw_1600_qspiboot_sben.bin"
            fi
            ;;
        auto)
            rcwimg="${RCWAUTO}${RCW_SUFFIX}"
            ;;
        sd)
            rcwimg="${RCWSD}${RCW_SUFFIX}"
            ;;
        emmc)
            rcwimg="${RCWEMMC}${RCW_SUFFIX}"
            ;;
        flexspi_nor)
            rcwimg="${RCWXSPI}${RCW_SUFFIX}"
            uefiboot="${UEFI_XSPIBOOT}"
            ;;        
        esac
            
	if [ -f "${DEPLOY_DIR_IMAGE}/rcw/${RCW_FOLDER}/${rcwimg}" ]; then
                oe_runmake V=1 -C ${S} realclean
                oe_runmake V=1 -C ${S} all fip pbl PLAT=${PLATFORM} BOOT_MODE=${d} RCW=${DEPLOY_DIR_IMAGE}/rcw/${RCW_FOLDER}/${rcwimg} BL33=${bl33} ${secureopt} ${fuseopt}
                cp -r ${S}/build/${PLATFORM}/release/bl2_${d}*.pbl ${S}
                cp -r ${S}/build/${PLATFORM}/release/fip.bin ${S}
                if [ "${BUILD_FUSE}" = "true" ]; then
                    cp -f ${S}/build/${PLATFORM}/release/fuse_fip.bin ${S}
                fi

                if [ -n "${PLATFORM_ADDITIONAL_TARGET}" ]; then
                    oe_runmake V=1 -C ${S} realclean
                    oe_runmake V=1 -C ${S} all fip pbl PLAT=${PLATFORM_ADDITIONAL_TARGET} BOOT_MODE=${d} RCW=${DEPLOY_DIR_IMAGE}/rcw/${RCW_FOLDER}/${rcwimg} BL33=${bl33} ${secureopt} ${fuseopt}
                    cp -r ${S}/build/${PLATFORM_ADDITIONAL_TARGET}/release/bl2_qspi${secext}.pbl ${S}/bl2_${d}${secext}_${PLATFORM_ADDITIONAL_TARGET}.pbl
                    cp -r ${S}/build/${PLATFORM_ADDITIONAL_TARGET}/release/fip.bin ${S}/fip_${PLATFORM_ADDITIONAL_TARGET}.bin
                    if [ "${BUILD_FUSE}" = "true" ]; then
                        cp -r ${S}/build/${PLATFORM_ADDITIONAL_TARGET}/release/fuse_fip.bin ${S}/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}.bin
                    fi
                fi
                if [ -n "${uefiboot}" -a -f "${DEPLOY_DIR_IMAGE}/uefi/${PLATFORM}/${uefiboot}" ]; then
                    oe_runmake V=1 -C ${S} realclean
                    oe_runmake V=1 -C ${S} all fip pbl PLAT=${PLATFORM} BOOT_MODE=${d} RCW=${DEPLOY_DIR_IMAGE}/rcw/${RCW_FOLDER}/${rcwimg} BL33=${DEPLOY_DIR_IMAGE}/uefi/${PLATFORM}/${uefiboot} ${secureopt} ${fuseopt}
                    cp -r ${S}/build/${PLATFORM}/release/fip.bin ${S}/fip_uefi.bin
                fi
        fi
        rcwimg=""
        uefiboot=""
    done
}

do_install() {
    install -d ${D}/boot/atf
    cp -r ${S}/srk.pri ${D}/boot/atf
    cp -r ${S}/srk.pub ${D}/boot/atf
    if [ "${BUILD_SECURE}" = "true" ]; then
        secext="_sec"
    fi
    if [ -f "${S}/fip_uefi.bin" ]; then
        cp -r ${S}/fip_uefi.bin ${D}/boot/atf/fip_uefi.bin
    fi
    if [ -f "${S}/fuse_fip.bin" ]; then
        cp -r ${S}/fuse_fip.bin ${D}/boot/atf/fuse_fip.bin
    fi
    if [ -f "${S}/fip.bin" ]; then
        cp -r ${S}/fip.bin ${D}/boot/atf/fip.bin
    fi
    for d in ${BOOTTYPE}; do
        if [ -e  ${S}/bl2_${d}${secext}.pbl ]; then
            cp -r ${S}/bl2_${d}${secext}.pbl ${D}/boot/atf/bl2_${d}${secext}.pbl
        fi
    done
    if [ -n "${PLATFORM_ADDITIONAL_TARGET}" ]; then
            cp -r ${S}/fip_${PLATFORM_ADDITIONAL_TARGET}.bin ${D}/boot/atf/fip_${PLATFORM_ADDITIONAL_TARGET}.bin
            cp -r ${S}/bl2_qspi${secext}_${PLATFORM_ADDITIONAL_TARGET}.pbl ${D}/boot/atf/bl2_qspi${secext}_${PLATFORM_ADDITIONAL_TARGET}.pbl
            if [ -f "${S}/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}.bin" ]; then
                cp -r ${S}/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}.bin ${D}/boot/atf/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}.bin
            fi
    fi
    chown -R root:root ${D}
    if [ -f "${S}/fip_ddr_sec.bin" ]; then
        cp -r ${S}/fip_ddr_sec.bin ${D}/boot/atf/fip_ddr_sec.bin
    fi
}

do_deploy() {
    install -d ${DEPLOYDIR}/atf
    cp -r ${D}/boot/atf/srk.pri ${DEPLOYDIR}/atf
    cp -r  ${D}/boot/atf/srk.pub ${DEPLOYDIR}/atf
    if [ "${BUILD_SECURE}" = "true" ]; then
        secext="_sec"
    fi
        
    if [ -f "${S}/fuse_fip.bin" ]; then
        cp -r ${D}/boot/atf/fuse_fip.bin ${DEPLOYDIR}/atf/fuse_fip${secext}.bin
    fi

    if [ -e ${D}/boot/atf/fip_uefi.bin ]; then
        cp -r ${D}/boot/atf/fip_uefi.bin ${DEPLOYDIR}/atf/fip_uefi.bin
    fi
    cp -r ${D}/boot/atf/fip.bin ${DEPLOYDIR}/atf/fip_uboot${secext}.bin
    for d in ${BOOTTYPE}; do
        if [ -e ${D}/boot/atf/bl2_${d}${secext}.pbl ]; then
            cp -r ${D}/boot/atf/bl2_${d}${secext}.pbl ${DEPLOYDIR}/atf/bl2_${d}${secext}.pbl
        fi
    done
    if [ -n "${PLATFORM_ADDITIONAL_TARGET}" ]; then
        cp -r ${S}/bl2_qspi${secext}_${PLATFORM_ADDITIONAL_TARGET}.pbl ${DEPLOYDIR}/atf/
        cp -r ${S}/fip_${PLATFORM_ADDITIONAL_TARGET}.bin ${DEPLOYDIR}/atf/fip_uboot${secext}_${PLATFORM_ADDITIONAL_TARGET}.bin
        if [ -f "${S}/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}.bin" ]; then
                cp -r ${S}/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}.bin ${D}/boot/atf/fuse_fip_${PLATFORM_ADDITIONAL_TARGET}${secext}.bin
        fi
    fi
    if [ -f "${S}/fip_ddr_sec.bin" ]; then
        cp -r ${D}/boot/atf/fip_ddr_sec.bin ${DEPLOYDIR}/atf/fip_ddr_sec.bin
    fi
}
addtask deploy after do_install
FILES_${PN} += "/boot"
BBCLASSEXTEND = "native nativesdk"
