# -*- coding: utf-8 -*-
#
# spray documentation build configuration file

import sys, os

# -- General configuration -----------------------------------------------------
extensions = ['sphinx.ext.todo']
source_suffix = '.rst'
master_doc = 'index'
exclude_patterns = ['_build']
templates_path = ['_templates']

# -- Project information -----------------------------------------------------
project = u'spray'
copyright = u'2012 by the spray team'
version = '1.0-M1'
release = '1.0-M1'
highlight_language = 'scala'
pygments_style = 'sphinx'
add_function_parentheses = False

# -- Options for HTML output ---------------------------------------------------

html_theme = 'default'
html_logo = 'logo.png'
htmlhelp_basename = 'spraydoc'

