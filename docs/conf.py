import sys, os

# -- General configuration -----------------------------------------------------

extensions = ['sphinx.ext.todo']
source_suffix = '.rst'
source_encoding = 'utf-8'
master_doc = 'index'
project = u'spray'
copyright = u'2011-2012 spray.cc.'
version = '1.0-M1'
release = '1.0-M1'
exclude_patterns = []

# -- Options for HTML output ---------------------------------------------------
html_theme = 'sprayed'
html_theme_path = ["./_themes"]
html_title = u'spray'
html_logo = u'logo.png'
html_static_path = ['_static']
html_use_smartypants = True
html_add_permalinks = None
htmlhelp_basename = 'spraydoc'
todo_include_todos = True

# -- Options for LaTeX output --------------------------------------------------
latex_elements = {
  'papersize': 'a4paper',
  'pointsize': '11pt',
}
latex_documents = [
  ('index', 'spray.tex', u'spray Documentation', u'spray.cc', 'manual'),
]
