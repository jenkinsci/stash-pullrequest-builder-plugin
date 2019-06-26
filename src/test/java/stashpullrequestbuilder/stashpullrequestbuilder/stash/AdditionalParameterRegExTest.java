package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.AbstractMap;
import java.util.Map;
import org.junit.Test;
import stashpullrequestbuilder.stashpullrequestbuilder.StashRepository;

/** Created by nathan on 7/06/2015. */
public class AdditionalParameterRegExTest {

  @Test
  public void testSingleParameter() throws Exception {
    assertThat(
        StashRepository.getParameter("p:customer=Nickesocke"),
        is(new AbstractMap.SimpleEntry<>("customer", "Nickesocke")));
  }

  @Test
  public void testSpacedParameter() throws Exception {
    assertThat(
        StashRepository.getParameter("p:customer=Nicke socke#"),
        is(new AbstractMap.SimpleEntry<>("customer", "Nicke socke#")));
  }

  @Test
  public void testBlankParameter() throws Exception {
    assertThat(
        StashRepository.getParameter("p:the_blank_parameteR="),
        is(new AbstractMap.SimpleEntry<>("the_blank_parameteR", "")));
  }

  @Test
  public void testInvalidParameter() throws Exception {
    assertThat(StashRepository.getParameter("p:if apa=Nickesocke"), is(nullValue()));

    assertThat(
        StashRepository.getParameter("p:apa==Nickesocke"),
        is(new AbstractMap.SimpleEntry<>("apa", "=Nickesocke")));

    assertThat(
        StashRepository.getParameter(
            "p:I want to make sure that a use of = will not trigger parameter"),
        is(nullValue()));

    assertThat(StashRepository.getParameter("p:=nothing"), is(nullValue()));

    assertThat(StashRepository.getParameter("=nothing"), is(nullValue()));
  }

  @Test
  public void testMultipleParameters() throws Exception {
    assertThat(StashRepository.getParametersFromContent(""), is(anEmptyMap()));

    Map<String, String> singleParameters =
        StashRepository.getParametersFromContent("p:param1=nothing");
    assertThat(singleParameters, allOf(is(aMapWithSize(1)), hasEntry("param1", "nothing")));

    Map<String, String> multipleParameters =
        StashRepository.getParametersFromContent(
            "p:param1=nothing\rp:param2=something special\np:param3=\r\r\n\rjumping to conclusions\r\r\n\n");
    assertThat(
        multipleParameters,
        allOf(
            is(aMapWithSize(3)),
            hasEntry("param1", "nothing"),
            hasEntry("param2", "something special"),
            hasEntry("param3", "")));
  }
}
