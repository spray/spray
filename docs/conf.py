import sys, os

# -- General configuration -----------------------------------------------------

sys.path.append(os.path.abspath('_sphinx/exts'))
extensions = ['sphinx.ext.todo', 'includecode', 'sphinx.ext.graphviz']
source_suffix = '.rst'
source_encoding = 'utf-8'
master_doc = 'index'
project = u'spray'
copyright = u'2011-2012 spray.io.'
version = '$VERSION$'
release = '$VERSION$'
exclude_patterns = []
highlight_language = 'scala'

# -- Options for HTML output ---------------------------------------------------
html_title = u'spray'
html_static_path = []
html_use_smartypants = True
html_add_permalinks = None
todo_include_todos = True
html_copy_source = False