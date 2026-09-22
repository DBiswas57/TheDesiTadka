package com.thedesitadka.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntibotSolverTest {

    private val sampleChallengeHtml = """
        <!DOCTYPE HTML>
        <html lang="en-US">
        <head>
          <meta charset="UTF-8" />
          <title>Just a moment...</title>
        </head>
        <body>
        <script>
        if (window.location.hostname !== window.atob("d2F0Y2h4eHhmcmVlLnh5eg==")) {
        window.location = window.atob("aHR0cDovL3dhdGNoeHh4ZnJlZS54eXovZXZpbC1hbmdlbC1taXNoYS1tYXZlci1hbnVza2F0enov");
        }
        function timer(){
         var obj=document.getElementById('timer');
         obj.innerHTML--;
         if(obj.innerHTML==0){
        setTimeout(function(){},1000);
        document.getElementById("btn").innerHTML = window.atob('PGZvcm0gYWN0aW9uPSIiIG1ldGhvZD0icG9zdCI+PGlucHV0IG5hbWU9ImFudGlib3QiIHR5cGU9ImhpZGRlbiIgdmFsdWU9IjM5YmE2NGQ1YjFmMGEzNmUxZjQwMTkzZTRjZGMxMzNiIj48aW5wdXQgdHlwZT0ic3VibWl0IiBuYW1lPSJzdWJtaXQiIHZhbHVlPSJDbGljayB0byBjb250aW51ZSI+PC9mb3JtPg==');
        }
        }
        </script>
        <div class="cf-browser-verification cf-im-under-attack">
          <h1>Checking your browser before accessing watchxxxfree.xyz</h1>
          <p id="btn">Please allow up to <span id="timer">5</span> seconds&hellip;</p>
        </div>
        <script>
        if (typeof antibot != "undefined") {
        if (antibot == window.atob("MzliYTY0ZDViMWYwYTM2ZTFmNDAxOTNlNGNkYzEzM2I=")) {
        var d = new Date();
        d.setTime(d.getTime() + (1*24*60*60*1000));
        var expires = "expires="+ d.toUTCString();
        document.cookie = "antibot=" + antibot + "; " + expires + "; path=/;";
        }
        }
        </script>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun testDetectsAntibotChallenge() {
        assertTrue(NetworkClient.isAntibotChallenge(sampleChallengeHtml))
    }

    @Test
    fun testNormalHtmlNotDetectedAsAntibot() {
        val normalHtml = "<html><head><title>Video Title</title></head><body><div class='content'>Hello</div></body></html>"
        assertFalse(NetworkClient.isAntibotChallenge(normalHtml))
    }

    @Test
    fun testExtractsAntibotTokenFromWindowAtob() {
        val token = NetworkClient.extractAntibotToken(sampleChallengeHtml)
        assertNotNull(token)
        assertEquals("39ba64d5b1f0a36e1f40193e4cdc133b", token)
    }

    @Test
    fun testExtractsAntibotTokenFromHiddenForm() {
        val formHtml = """
            <form action="" method="post">
                <input name="antibot" type="hidden" value="a1b2c3d4e5f67890123456789abcdef0">
                <input type="submit" value="Submit">
            </form>
        """.trimIndent()
        val token = NetworkClient.extractAntibotToken(formHtml)
        assertNotNull(token)
        assertEquals("a1b2c3d4e5f67890123456789abcdef0", token)
    }
}
