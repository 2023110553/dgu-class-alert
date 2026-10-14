package kr.ac.dongguk.classalert

import kr.ac.dongguk.classalert.data.CourseCatalog
import org.junit.Assert.*
import org.junit.Test

class CourseCatalogTest {
    @Test fun extractsCourseCodeFromEclassCategory() {
        assertEquals("CSC2005", CourseCatalog.extractCode("2026_20_CM015.110_DS034101_CSC2005_01"))
        assertEquals("PRI4041", CourseCatalog.extractCode("2026_20_CM015.110_DS030738_PRI4041_02"))
    }

    @Test fun ignoresOpaqueCategoryHash() {
        assertNull(CourseCatalog.extractCode("9fdb46b978fe6cc0be2f179d0a5042fe"))
        assertNull(CourseCatalog.extractCode(null))
    }
}
