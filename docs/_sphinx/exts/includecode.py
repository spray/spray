import os
import codecs
from os import path

from docutils import nodes
from docutils.parsers.rst import Directive, directives

class IncludeCode(Directive):
    """
    Include a code example from a file with sections delimited with special comments.
    """

    has_content = False
    required_arguments = 1
    optional_arguments = 0
    final_argument_whitespace = False
    option_spec = {
        'example':      directives.unchanged_required
    }

    def run(self):
        document = self.state.document
        arg0 = self.arguments[0]
        (filename, sep, section) = arg0.partition('#')

        if not document.settings.file_insertion_enabled:
            return [document.reporter.warning('File insertion disabled',
                                              line=self.lineno)]
        env = document.settings.env
        if filename.startswith('/') or filename.startswith(os.sep):
            rel_fn = filename[1:]
        else:
            docdir = path.dirname(env.doc2path(env.docname, base=None))
            rel_fn = path.join(docdir, filename)
        try:
            fn = path.join(env.srcdir, rel_fn)
        except UnicodeDecodeError:
            # the source directory is a bytestring with non-ASCII characters;
            # let's try to encode the rel_fn in the file system encoding
            rel_fn = rel_fn.encode(sys.getfilesystemencoding())
            fn = path.join(env.srcdir, rel_fn)

        encoding = self.options.get('encoding', env.config.source_encoding)
        codec_info = codecs.lookup(encoding)
        try:
            f = codecs.StreamReaderWriter(open(fn, 'U'),
                    codec_info[2], codec_info[3], 'strict')
            lines = f.readlines()
            f.close()
        except (IOError, OSError):
            return [document.reporter.warning(
                'Include file %r not found or reading it failed' % filename,
                line=self.lineno)]
        except UnicodeError:
            return [document.reporter.warning(
                'Encoding %r used for reading included file %r seems to '
                'be wrong, try giving an :encoding: option' %
                (encoding, filename))]

        example = self.options.get('example')
        current_examples = ""
        res = []
        for line in lines:
            comment = line.split("//", 1)[1] if line.find("//") > 0 else ""
            if len(line) > 2 and line[2] == '"':
                current_examples = line[2:]
            elif len(line) > 2 and line[2] == '}':
                current_examples = ""
            elif current_examples.find(example) >= 0 and comment.find("hide") == -1:
                res.append(line[4:].rstrip() + '\n')
            elif comment.find(example) >= 0:
                res.append(line.split("//", 1)[0].strip() + '\n')

        text = ''.join(res)
        retnode = nodes.literal_block(text, text, source=fn)
        document.settings.env.note_dependency(rel_fn)
        return [retnode]

def setup(app):
    app.require_sphinx('1.0')
    app.add_directive('includecode', IncludeCode)
