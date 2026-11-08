package com.example.aitask.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证 Provider 必须精确命中，不会自动回退到其他厂商。 */
class AiProviderSelectorTest {

    /** 已启用厂商返回对应实例，未启用厂商直接失败。 */
    @Test
    void selectsExactProviderWithoutFallback() {
        AiProvider openAi = mock(AiProvider.class);
        when(openAi.provider()).thenReturn("openai");
        AiProviderSelector selector = new AiProviderSelector(List.of(openAi));

        assertThat(selector.select("openai")).isSameAs(openAi);
        assertThatThrownBy(() -> selector.select("deepseek"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
