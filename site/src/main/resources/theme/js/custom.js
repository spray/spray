$(function() {
    var cookieName = "ack-spray-deprecation-note";

    function getCookieValue(cname) {
        var name = cname + "=";
        var ca = document.cookie.split(';');
        for(var i=0; i<ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1);
            if (c.indexOf(name) == 0) return c.substring(name.length,c.length);
        }
        return "";
    }

    if (getCookieValue(cookieName) !== "1") {
        var noteDiv = $("#deprecation-note");
        noteDiv.show();
        noteDiv.find("button").click(function () {
            var d = new Date();
            d.setTime(d.getTime() + (24*3600*1000));
            document.cookie = cookieName + "=1; path=/; expires=" + d.toUTCString();
            noteDiv.hide();
        });
    }
});