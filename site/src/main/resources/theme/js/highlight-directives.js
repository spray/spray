$(function() {
    // disabled for old milestone versions (containing 'M' in the version)
    var versionRE = new RegExp('/documentation/([^/M]*)/.*')
    var match = versionRE.exec(document.location.pathname);

    if (match) {
        var version = match[1]; // capture group 1

        var directives = [];
        // convert directivesMap into a flat list of {group, name} entries, only called once per page
        function init() {
            for (var i in DirectivesMap) {
                var group = DirectivesMap[i];
                var entries = group.entries.split(' ');
                for (var j in entries) {
                    var d = entries[j];
                    directives.push({
                        group: group.group+'-directives',
                        name: d
                    });
                }
            }
        }

        function findDirective(name) {
            for (var i in directives) {
                var t = directives[i];
                if (t.name === name)
                    return t;
            }
        }
        function directiveLinkTarget(directive) {
            return '/documentation/'+version+'/spray-routing/'+directive.group+'/'+directive.name+'/';
        }
        init();

        $('.highlight-scala .n').each(function(i, e) {
            // crude heuristic to exclude false positives in "ctx.request.method" or "ctx.complete"
            if (e.previousSibling && e.previousSibling.textContent === ".") return;
            var ele = $(e);
            var name = ele.text();
            var directive = findDirective(name);
            if (directive)
                ele.wrap('<a href="'+directiveLinkTarget(directive)+'"/>');
        });
    }
});
