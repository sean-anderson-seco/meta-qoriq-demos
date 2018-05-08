SUMMARY = "NXP kubernetes configuration scripts"
HOMEPAGE = "https://github.com/nxp/qoriq-eds-kubelet.git"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://EULA.txt;md5=d969f2c93b3905d4b628787ce5f8df4b"

SRC_URI = "git://github.com/NXP/qoriq-eds-kubelet.git;nobranch=1"
SRCREV = "4b51fccefe4620ccb44c9639055a0c7db8ddd87f"

RDEPENDS_${PN} = "qoriq-eds-kubelet"

S = "${WORKDIR}/git"

do_compile[noexec] = "1"

do_install() {
    install -d ${D}/${bindir}/
    install -d ${D}/${sysconfdir}/
    install -m 655 ${S}/etc/kubernetes/* ${D}/${sysconfdir}
    install -m 655 ${S}/scripts/*  ${D}/${bindir}
}

