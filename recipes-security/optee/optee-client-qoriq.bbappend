FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"
SRC_URI = "git://bitbucket.sw.nxp.com/scm/dash/optee_client.git;protocol=https;nobranch=1 \
    file://0001-GCC-8-format-truncation-error.patch \
    file://0001-flags-CFLAGS-add-Wno-cpp.patch \
"

SRCREV = "ab3c79ccd3ea9323e236d30037977c0a19944dbd"

