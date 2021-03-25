# Copyright 2020 NXP

SUMMARY = "NXP CAAM Keyctl"
DESCRIPTION = "NXP keyctl tool to manage CAAM Keys"
SECTION = "base"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://COPYING;md5=8636bd68fc00cc6a3809b7b58b45f982"

KEYCTL_CAAM_SRCBRANCH ?= "master"
KEYCTL_CAAM_SRC ?= "git://bitbucket.sw.nxp.com/ssm/keyctl_caam.git;protocol=ssh;nobranch=1"
SRC_URI = "${KEYCTL_CAAM_SRC};branch=${KEYCTL_CAAM_SRCBRANCH}"

SRCREV = "6b80882e3d5bc986a1f2f9512845170658ba9ea2"

S = "${WORKDIR}/git"

TARGET_CC_ARCH += "${LDFLAGS}"

do_install () {
	oe_runmake DESTDIR=${D} install
}

COMPATIBLE_MACHINE = "(imx|qoriq)"