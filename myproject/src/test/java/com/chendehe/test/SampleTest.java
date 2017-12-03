package com.chendehe.test;

import com.chendehe.service.SampleService;
import com.chendehe.vo.UserVo;

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;


/**
 * SpringRunner指明是测试类
 * SpringBootTest创建ApplicationContext,其他的还有@…​Test
 * 使用随机端口配置RANDOM_PORT，其他MOCK、DEFINED_PORT、NONE
 * 自动扫描同一路径下的class文件进行测试
 * 导入测试依赖的类 @Import(MyTestsConfiguration.class)
 * 自定义配置 @AutoConfigure…
 * exclude 排除测试 @ImportAutoConfiguration(exclude = SampleService.class)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class SampleTest {

    // controller
    @Autowired
    private TestRestTemplate template;

    // mock
    @MockBean
    private SampleService sampleService;

    @Test
    public void controllerTest() {
        String body = template.getForObject("/", String.class);
        assertThat(body).isEqualTo("Hello World!");
    }

    @Test
    public void mockTest() {
        given(sampleService.findAll()).willReturn(Lists.newArrayList());
        List<UserVo> all = sampleService.findAll();
        assertThat(all).isEmpty();
    }

}