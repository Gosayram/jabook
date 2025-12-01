#!/usr/bin/env python3
# Copyright 2025 Jabook Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Create adaptive icon background with solid color."""

import sys
from PIL import Image

def create_solid_color_image(size, color, output_path):
    """Create a solid color image."""
    img = Image.new('RGB', (size, size), color)
    img.save(output_path, 'PNG')
    print(f"Created {output_path} ({size}x{size}) with color {color}")

if __name__ == '__main__':
    # Beta logo background color: #A9B65F (RGB: 169, 182, 95)
    color = (169, 182, 95)
    size = 1024
    output_path = sys.argv[1] if len(sys.argv) > 1 else 'app_icon_adaptive_bg.png'
    
    try:
        create_solid_color_image(size, color, output_path)
    except ImportError:
        print("Error: PIL (Pillow) is required. Install with: pip install Pillow")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

