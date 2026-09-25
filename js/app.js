/**
 * TheDesiTadka Official Web App Logic
 * Interactive handlers, release dynamic check, copy to clipboard, and FAQ interactions.
 */

document.addEventListener('DOMContentLoaded', () => {
  initNavbar();
  initFaqAccordion();
  initHashCopy();
  initReleaseMetadata();
});

// Sticky Navbar Scroll Effect
function initNavbar() {
  const header = document.querySelector('.site-header');
  const mobileToggle = document.querySelector('.mobile-toggle');
  const navMenu = document.querySelector('.nav-menu');
  const navLinks = document.querySelectorAll('.nav-link');

  window.addEventListener('scroll', () => {
    if (window.scrollY > 30) {
      header.classList.add('scrolled');
    } else {
      header.classList.remove('scrolled');
    }
  });

  // Mobile menu toggle
  if (mobileToggle && navMenu) {
    mobileToggle.addEventListener('click', () => {
      navMenu.classList.toggle('open');
      const isOpen = navMenu.classList.contains('open');
      mobileToggle.setAttribute('aria-expanded', isOpen);
    });

    // Close when link clicked
    navLinks.forEach(link => {
      link.addEventListener('click', () => {
        navMenu.classList.remove('open');
      });
    });
  }
}

// Copy SHA256 Checksum
function initHashCopy() {
  const copyBtn = document.getElementById('copyHashBtn');
  const hashElement = document.getElementById('apkHashValue');

  if (copyBtn && hashElement) {
    copyBtn.addEventListener('click', async () => {
      const hashText = hashElement.innerText.trim();
      try {
        await navigator.clipboard.writeText(hashText);
        const originalText = copyBtn.innerText;
        copyBtn.innerText = 'Copied!';
        copyBtn.style.borderColor = 'var(--primary)';
        copyBtn.style.color = '#fff';

        setTimeout(() => {
          copyBtn.innerText = originalText;
          copyBtn.style.borderColor = '';
          copyBtn.style.color = '';
        }, 2000);
      } catch (err) {
        // Fallback for older browsers
        const textarea = document.createElement('textarea');
        textarea.value = hashText;
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);

        copyBtn.innerText = 'Copied!';
        setTimeout(() => {
          copyBtn.innerText = 'Copy Hash';
        }, 2000);
      }
    });
  }
}

// FAQ Accordion
function initFaqAccordion() {
  const faqItems = document.querySelectorAll('.faq-item');

  faqItems.forEach(item => {
    const questionBtn = item.querySelector('.faq-question');
    if (questionBtn) {
      questionBtn.addEventListener('click', () => {
        const isActive = item.classList.contains('active');

        // Close others
        faqItems.forEach(other => {
          if (other !== item) {
            other.classList.remove('active');
          }
        });

        // Toggle current
        item.classList.toggle('active', !isActive);
      });
    }
  });
}

// Dynamic Release Metadata from GitHub (Graceful Fallback)
async function initReleaseMetadata() {
  const repo = 'DBiswas57/TheDesiTadka';
  const apiUrl = `https://api.github.com/repos/${repo}/releases/latest`;

  try {
    const response = await fetch(apiUrl);
    if (!response.ok) return;

    const data = await response.json();
    if (!data.tag_name) return;

    // Update release tags if elements exist
    const versionBadges = document.querySelectorAll('.dynamic-version-tag');
    versionBadges.forEach(el => {
      el.innerText = data.tag_name;
    });

    const publishedDate = document.getElementById('releaseDateValue');
    if (publishedDate && data.published_at) {
      const date = new Date(data.published_at);
      publishedDate.innerText = date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    }

    // Direct GitHub release asset download link
    const apkAsset = data.assets && data.assets.find(a => a.name.endsWith('.apk'));
    if (apkAsset) {
      const githubDownloadLinks = document.querySelectorAll('.github-direct-download');
      githubDownloadLinks.forEach(link => {
        link.href = apkAsset.browser_download_url;
      });
    }
  } catch (err) {
    // Offline or rate-limited; gracefully uses static v1.1.9 defaults
    console.log('GitHub Release API fallback to embedded v1.1.9');
  }
}
