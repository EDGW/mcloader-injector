package com.kako.chan.mcloader.injector.agent;

import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

/** Configuration precedence and boundaries consumers rely on. */
public class RuleTableTest {
    @Test public void mergesSystemPropertiesAndIgnoresAgentArgs() {
        Properties properties = new Properties();
        properties.setProperty("mcloader.injector.table", "http://a.invalid/=>http://mirror.invalid/a/");
        properties.setProperty("mcloader.injector.rule.named", "http://a.invalid/nested/=>http://mirror.invalid/b/");
        properties.setProperty("mcloader.injector.rule", "http://b.invalid/=>http://mirror.invalid/c/");
        properties.setProperty("mcloader.injector.rules", "not a rule property");
        RuleTable result = RuleTable.parse("invalid ignored agent arguments", properties);
        assertArrayEquals(new String[] {"http://a.invalid/nested/", "http://a.invalid/", "http://b.invalid/"}, result.from);
        assertArrayEquals(new String[] {"http://mirror.invalid/b/", "http://mirror.invalid/a/", "http://mirror.invalid/c/"}, result.to);
    }

    @Test public void agentArgumentsAreUsedWithoutSystemRules() {
        RuleTable result = RuleTable.parse("https://a.invalid/=>https://a.invalid/|http://b.invalid/=>http://c.invalid/", new Properties());
        assertArrayEquals(new String[] {"https://a.invalid/", "http://b.invalid/"}, result.from);
        assertEquals(result.from[0], result.to[0]);
    }

    @Test public void rejectsDuplicateSourcesAcrossPropertyInputs() {
        Properties properties = new Properties();
        properties.setProperty("mcloader.injector.rule.1", "http://a.invalid/=>http://b.invalid/");
        properties.setProperty("mcloader.injector.rule.2", "http://a.invalid/=>http://c.invalid/");
        assertThrows(IllegalArgumentException.class, () -> RuleTable.parse(null, properties));
    }

    @Test public void rejectsMalformedRulesWithoutFallingBack() {
        for (String value : new String[] {"", "http://a.invalid/", "http://a.invalid/=>", "file:/a=>http://b.invalid/",
                "http:///a=>http://b.invalid/", "http://a.invalid/=>http://bad host/", "http://a.invalid/=>http://b.invalid/|"}) {
            Properties properties = new Properties();
            properties.setProperty("mcloader.injector.table", value);
            assertThrows(value, IllegalArgumentException.class,
                    () -> RuleTable.parse("http://valid.invalid/=>http://mirror.invalid/", properties));
        }
        assertThrows(IllegalArgumentException.class, () -> RuleTable.parse(null, new Properties()));
    }
}
